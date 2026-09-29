package com.github.yildizmy.controller;

import com.github.yildizmy.config.AuthCookieProperties;
import com.github.yildizmy.dto.request.LoginRequest;
import com.github.yildizmy.dto.request.SignupRequest;
import com.github.yildizmy.dto.response.AuthSessionResponse;
import com.github.yildizmy.dto.response.CommandResponse;
import com.github.yildizmy.security.UserDetailsImpl;
import com.github.yildizmy.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String CLEAR_SITE_DATA = "\"cache\", \"cookies\", \"storage\"";
    private static final String CLEAR_SITE_DATA_HEADER = "Clear-Site-Data";

    private final AuthService authService;
    private final AuthCookieProperties cookieProperties;

    public AuthController(AuthService authService, AuthCookieProperties cookieProperties) {
        this.authService = authService;
        this.cookieProperties = cookieProperties;
    }

    /**
     * Authenticates users by their credentials.
     *
     * @param request
     * @return authenticated user without session credentials
     */
    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@Valid @RequestBody LoginRequest request) {
        return sessionResponse(authService.login(request));
    }

    /**
     * Registers users using their credentials and user info.
     *
     * @param request
     * @return id of the registered user
     */
    @PostMapping("/signup")
    public ResponseEntity<CommandResponse> signup(@Valid @RequestBody SignupRequest request) {
        final CommandResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(HttpServletRequest request) {
        return sessionResponse(authService.refresh(requiredRefreshToken(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Cookie refreshCookie = WebUtils.getCookie(request, cookieProperties.refreshName());
        if (refreshCookie != null && StringUtils.hasText(refreshCookie.getValue())) {
            authService.logout(refreshCookie.getValue());
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieProperties.clearAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieProperties.clearRefreshCookie().toString())
                .header(CLEAR_SITE_DATA_HEADER, CLEAR_SITE_DATA)
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthSessionResponse> me(@AuthenticationPrincipal UserDetailsImpl principal) {
        return noStore(ResponseEntity.ok()).body(authService.currentUser(principal));
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private ResponseEntity<AuthSessionResponse> sessionResponse(AuthService.AuthenticatedSession session) {
        Duration accessMaxAge = remaining(session.accessToken().expiresAt());
        Duration refreshMaxAge = remaining(session.refreshToken().expiresAt());
        return noStore(ResponseEntity.ok())
                .header(HttpHeaders.SET_COOKIE,
                        cookieProperties.accessCookie(session.accessToken().value(), accessMaxAge).toString())
                .header(HttpHeaders.SET_COOKIE,
                        cookieProperties.refreshCookie(session.refreshToken().value(), refreshMaxAge).toString())
                .body(session.user());
    }

    private String requiredRefreshToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, cookieProperties.refreshName());
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            throw new BadCredentialsException("Authentication failed");
        }
        return cookie.getValue();
    }

    private static Duration remaining(Instant expiresAt) {
        Duration duration = Duration.between(Instant.now(), expiresAt);
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    private static ResponseEntity.BodyBuilder noStore(ResponseEntity.BodyBuilder builder) {
        return builder.header(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
