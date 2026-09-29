package com.github.yildizmy.controller;

import com.github.yildizmy.auth.RefreshSessionService;
import com.github.yildizmy.config.SecurityConfig;
import com.github.yildizmy.dto.request.LoginRequest;
import com.github.yildizmy.dto.response.AuthSessionResponse;
import com.github.yildizmy.exception.GlobalExceptionHandler;
import com.github.yildizmy.security.AuthEntryPointJwt;
import com.github.yildizmy.security.JwtUtils;
import com.github.yildizmy.security.UserDetailsImpl;
import com.github.yildizmy.security.UserDetailsServiceImpl;
import com.github.yildizmy.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class AuthControllerIT {

    private static final Instant EXPIRY = Instant.now().plus(Duration.ofDays(30));

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
    void loginSetsHttpOnlyCookiesAndReturnsOnlySanitizedUserData() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(session("access-token", "refresh-token"));
        Cookie csrf = csrfCookie();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"username":"buyer","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().value("TEST_ACCESS", "access-token"))
                .andExpect(cookie().httpOnly("TEST_ACCESS", true))
                .andExpect(cookie().secure("TEST_ACCESS", true))
                .andExpect(cookie().path("TEST_ACCESS", "/api/v1"))
                .andExpect(cookie().value("TEST_REFRESH", "refresh-token"))
                .andExpect(cookie().httpOnly("TEST_REFRESH", true))
                .andExpect(cookie().secure("TEST_REFRESH", true))
                .andExpect(cookie().path("TEST_REFRESH", "/api/v1/auth"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.username").value("buyer"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("access-token", "refresh-token", "password", "token");
    }

    @Test
    void refreshRotatesTheCookiePairAndReturnsTheSamePublicContract() throws Exception {
        when(authService.refresh("old-refresh")).thenReturn(session("new-access", "new-refresh"));
        Cookie csrf = csrfCookie();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(csrf, new Cookie("TEST_REFRESH", "old-refresh"))
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(cookie().value("TEST_ACCESS", "new-access"))
                .andExpect(cookie().value("TEST_REFRESH", "new-refresh"))
                .andExpect(jsonPath("$.username").value("buyer"));

        verify(authService).refresh("old-refresh");
    }

    @Test
    void refreshWithoutItsCookieFailsWithTheGenericAuthenticationProblem() throws Exception {
        Cookie csrf = csrfCookie();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("Authentication is required."));

        verifyNoInteractions(authService);
    }

    @Test
    void logoutRevokesTheRefreshSessionAndClearsBrowserState() throws Exception {
        Cookie csrf = csrfCookie();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(csrf, new Cookie("TEST_REFRESH", "refresh-to-revoke"))
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("TEST_ACCESS", 0))
                .andExpect(cookie().maxAge("TEST_REFRESH", 0))
                .andExpect(header().string("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\""))
                .andExpect(content().string(""));

        verify(authService).logout("refresh-to-revoke");
    }

    @Test
    void meUsesTheAuthenticatedPrincipalAndNeverReturnsCredentials() throws Exception {
        UserDetailsImpl principal = principal();
        when(authService.currentUser(principal)).thenReturn(userResponse());

        MvcResult result = mockMvc.perform(get("/api/v1/auth/me").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("buyer"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("password", "token");
    }

    @Test
    void csrfEndpointIssuesTheSpaCookieWithoutPuttingTheTokenInTheBody() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(content().string(""));
    }

    @Test
    void invalidUsernameAndInvalidPasswordHaveTheSamePublicError() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UsernameNotFoundException("buyer does not exist"))
                .thenThrow(new BadCredentialsException("password was wrong"));
        Cookie csrf = csrfCookie();

        String unknownUser = failedLogin(csrf, "same-request-id");
        String badPassword = failedLogin(csrf, "same-request-id");

        assertThat(unknownUser).isEqualTo(badPassword)
                .doesNotContain("does not exist", "password was wrong", "buyer");
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

    private String failedLogin(Cookie csrf, String requestId) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .header("X-Request-ID", requestId)
                        .contentType("application/json")
                        .content("""
                                {"username":"buyer","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static AuthService.AuthenticatedSession session(String accessToken, String refreshToken) {
        return new AuthService.AuthenticatedSession(
                userResponse(),
                new JwtUtils.AccessToken(accessToken, EXPIRY),
                new RefreshSessionService.RefreshToken(refreshToken, EXPIRY, 7L)
        );
    }

    private static AuthSessionResponse userResponse() {
        return new AuthSessionResponse(7L, "buyer", "Test", "Buyer", List.of("ROLE_USER"));
    }

    private static UserDetailsImpl principal() {
        return new UserDetailsImpl(
                7L,
                "buyer",
                "not-serialized",
                "Test",
                "Buyer",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
