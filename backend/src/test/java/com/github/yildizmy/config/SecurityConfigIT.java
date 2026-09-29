package com.github.yildizmy.config;

import com.github.yildizmy.security.AuthEntryPointJwt;
import com.github.yildizmy.security.JwtUtils;
import com.github.yildizmy.security.UserDetailsImpl;
import com.github.yildizmy.security.UserDetailsServiceImpl;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTestController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("security-config-test")
@TestPropertySource(properties = {
        "app.security.cookies.access-name=TEST_ACCESS",
        "app.security.cookies.refresh-name=TEST_REFRESH",
        "app.security.cookies.secure=true",
        "app.security.cookies.same-site=Lax"
})
class SecurityConfigIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthCookieProperties cookieProperties;

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
    void browserMutationRequiresTheXsrfCookieAndHeaderPair() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isForbidden());

        Cookie csrfCookie = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk());
    }

    @Test
    void webhookPostBypassesUserJwtAndCsrfOnlyInsideTheDedicatedChain() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/momo"))
                .andExpect(status().isOk());
        verifyNoInteractions(jwtUtils, userDetailsService);

        mockMvc.perform(get("/api/v1/webhooks/momo"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/webhook/momo"))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessCookieAuthenticatesWithoutExposingTheTokenToJavascript() throws Exception {
        String accessToken = "sensitive-access-token";
        when(jwtUtils.validateJwtToken(accessToken)).thenReturn(true);
        when(jwtUtils.getUsernameFromJwtToken(accessToken)).thenReturn("buyer");
        when(userDetailsService.loadUserByUsername("buyer")).thenReturn(new UserDetailsImpl(
                1L,
                "buyer",
                "ignored",
                "Test",
                "Buyer",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));

        mockMvc.perform(get("/api/v1/protected")
                        .cookie(new Cookie(cookieProperties.accessName(), accessToken)))
                .andExpect(status().isOk());
    }

    @Test
    void productionCookieTemplatesUseSecureHttpOnlyNarrowPaths() {
        ResponseCookie access = cookieProperties.accessCookie("access-value", Duration.ofMinutes(5));
        ResponseCookie refresh = cookieProperties.refreshCookie("refresh-value", Duration.ofDays(30));

        assertThat(access.isHttpOnly()).isTrue();
        assertThat(access.isSecure()).isTrue();
        assertThat(access.getSameSite()).isEqualTo("Lax");
        assertThat(access.getPath()).isEqualTo("/api/v1");
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.isSecure()).isTrue();
        assertThat(refresh.getSameSite()).isEqualTo("Lax");
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
    }

}

@RestController
@Profile("security-config-test")
class SecurityConfigTestController {

    @GetMapping("/api/v1/auth/csrf")
    String csrf() {
        return "csrf";
    }

    @PostMapping("/api/v1/auth/login")
    String login() {
        return "login";
    }

    @GetMapping("/api/v1/protected")
    String protectedRoute() {
        return "protected";
    }

    @PostMapping("/api/v1/webhooks/momo")
    String webhook() {
        return "webhook";
    }

    @GetMapping("/api/v1/webhooks/momo")
    String webhookGet() {
        return "webhook";
    }
}
