package com.github.yildizmy.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitIT {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-28T00:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void scopesAuthAndCheckoutBucketsByEndpointAndReturnsRetryAfterWhenExceeded() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(properties(1), clock);

        MockHttpServletResponse firstLogin = invoke(filter, post("/api/v1/auth/login"), new AtomicInteger());
        MockHttpServletResponse secondLogin = invoke(filter, post("/api/v1/auth/login"), new AtomicInteger());
        MockHttpServletResponse checkout = invoke(filter, post("/api/v1/orders"), new AtomicInteger());

        assertThat(firstLogin.getStatus()).isEqualTo(200);
        assertThat(secondLogin.getStatus()).isEqualTo(429);
        assertThat(secondLogin.getHeader("Retry-After")).isEqualTo("60");
        assertThat(secondLogin.getContentAsString()).doesNotContain("token", "signature", "secret");
        assertThat(checkout.getStatus()).isEqualTo(200);
    }

    @Test
    void usesAuthenticatedPrincipalInsteadOfSharingOneIpBucketForProtectedRoutes() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(properties(1), clock);
        MockHttpServletRequest request = post("/api/v1/orders");

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("buyer-a", "ignored", java.util.List.of()));
        MockHttpServletResponse buyerA = invoke(filter, request, new AtomicInteger());

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("buyer-b", "ignored", java.util.List.of()));
        MockHttpServletResponse buyerB = invoke(filter, post("/api/v1/orders"), new AtomicInteger());

        assertThat(buyerA.getStatus()).isEqualTo(200);
        assertThat(buyerB.getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotTrustForwardedForForUnauthenticatedIdentity() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(properties(1), clock);
        MockHttpServletRequest first = post("/api/v1/auth/login");
        first.addHeader("X-Forwarded-For", "attacker-controlled-a");
        MockHttpServletRequest second = post("/api/v1/auth/login");
        second.addHeader("X-Forwarded-For", "attacker-controlled-b");

        assertThat(invoke(filter, first, new AtomicInteger()).getStatus()).isEqualTo(200);
        assertThat(invoke(filter, second, new AtomicInteger()).getStatus()).isEqualTo(429);
    }

    private static RateLimitFilter.RateLimitProperties properties(int limit) {
        return new RateLimitFilter.RateLimitProperties(
                true,
                Duration.ofSeconds(60),
                limit,
                limit,
                limit,
                limit,
                limit,
                limit,
                limit,
                1000);
    }

    private static MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("192.0.2.10");
        request.setContent("sensitive request body with signature".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request;
    }

    private static MockHttpServletResponse invoke(
            RateLimitFilter filter,
            MockHttpServletRequest request,
            AtomicInteger chainCalls
    ) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            chainCalls.incrementAndGet();
            response.setStatus(200);
        });
        return response;
    }
}
