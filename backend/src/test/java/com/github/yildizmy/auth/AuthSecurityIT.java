package com.github.yildizmy.auth;

import com.github.yildizmy.config.SecurityConfig;
import com.github.yildizmy.controller.AuthController;
import com.github.yildizmy.domain.entity.User;
import com.github.yildizmy.exception.GlobalExceptionHandler;
import com.github.yildizmy.security.AuthEntryPointJwt;
import com.github.yildizmy.security.JwtUtils;
import com.github.yildizmy.security.UserDetailsServiceImpl;
import com.github.yildizmy.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.cookies.access-name=TEST_ACCESS",
        "app.security.cookies.refresh-name=TEST_REFRESH",
        "app.security.cookies.secure=true",
        "app.security.cookies.same-site=Lax"
})
class AuthSecurityIT {

    private static final Instant START = Instant.parse("2026-09-17T09:00:00Z");
    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthEntryPointJwt authEntryPointJwt;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @BeforeEach
    void configureUnauthorizedResponse() throws Exception {
        doAnswer(invocation -> {
            invocation.<jakarta.servlet.http.HttpServletResponse>getArgument(1).sendError(401);
            return null;
        }).when(authEntryPointJwt).commence(any(), any(), any());
    }

    @Test
    void unsafeAuthenticationRequestWithoutCsrfNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"buyer","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }

    @Test
    void reusedRefreshTokenReturnsOnlyTheGenericAuthenticationProblem() throws Exception {
        when(authService.refresh("reused-refresh-token"))
                .thenThrow(new BadCredentialsException("Refresh token is invalid"));
        Cookie csrf = csrfCookie();

        String response = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(csrf, new Cookie("TEST_REFRESH", "reused-refresh-token"))
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("Authentication is required."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("reused-refresh-token", "Refresh token is invalid");
    }

    @Test
    void reusedRefreshTokenRevokesEverySessionInItsFamily() {
        RefreshSessionRepository repository = mock(RefreshSessionRepository.class);
        User user = persistedUser(7L);
        UUID familyId = UUID.randomUUID();
        String reusedToken = "already-rotated-refresh-token";
        RefreshSession consumed = session(user, familyId, reusedToken, START);
        RefreshSession successor = session(user, familyId, "successor-refresh-token", START.plusSeconds(1));
        consumed.rotateTo(successor, START.plusSeconds(1));
        when(repository.findFamilyRootByTokenHashForUpdate(RefreshTokenHash.fromToken(reusedToken)))
                .thenReturn(java.util.Optional.of(consumed));
        when(repository.findFamilyByFamilyIdForUpdate(familyId)).thenReturn(List.of(consumed, successor));
        RefreshSessionService service = new RefreshSessionService(
                repository,
                REFRESH_TTL,
                new SecureRandom(),
                Clock.fixed(START.plusSeconds(2), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.rotate(reusedToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Refresh token is invalid");

        assertThat(consumed.getRevocationReason()).isEqualTo("refresh-token-reuse");
        assertThat(successor.getRevocationReason()).isEqualTo("refresh-token-reuse");
        assertThat(consumed.getRevokedAt()).isEqualTo(START.plusSeconds(2));
        assertThat(successor.getRevokedAt()).isEqualTo(START.plusSeconds(2));
    }

    private Cookie csrfCookie() throws Exception {
        Cookie cookie = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static User persistedUser(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static RefreshSession session(
            User user,
            UUID familyId,
            String rawToken,
            Instant createdAt
    ) {
        return new RefreshSession(
                user,
                familyId,
                RefreshTokenHash.fromToken(rawToken),
                createdAt,
                createdAt.plus(REFRESH_TTL)
        );
    }
}
