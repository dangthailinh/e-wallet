package com.github.yildizmy.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersIT {

    @Test
    void emitsBrowserBoundaryHeadersWithoutHstsOnPlainHttp() throws Exception {
        SecurityHeadersConfig filter = new SecurityHeadersConfig(properties(true, true));
        MockHttpServletResponse response = invoke(filter, request(false, null));

        assertThat(response.getHeader("Content-Security-Policy"))
                .isEqualTo("default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }

    @Test
    void emitsHstsOnlyForSecureRequestsWhenEnabled() throws Exception {
        SecurityHeadersConfig filter = new SecurityHeadersConfig(properties(true, true));
        MockHttpServletResponse response = invoke(filter, request(true, null));

        assertThat(response.getHeader("Strict-Transport-Security"))
                .isEqualTo("max-age=31536000; includeSubDomains");
    }

    @Test
    void doesNotTreatUntrustedForwardedProtoAsTls() throws Exception {
        SecurityHeadersConfig filter = new SecurityHeadersConfig(properties(true, false));
        MockHttpServletResponse response = invoke(filter, request(false, "https"));

        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }

    private static SecurityHeadersConfig.SecurityHeadersProperties properties(
            boolean hstsEnabled,
            boolean trustForwardedProto
    ) {
        return new SecurityHeadersConfig.SecurityHeadersProperties(
                true,
                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
                "DENY",
                "no-referrer",
                hstsEnabled,
                true,
                false,
                31536000,
                trustForwardedProto);
    }

    private static MockHttpServletRequest request(boolean secure, String forwardedProto) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.setSecure(secure);
        if (forwardedProto != null) {
            request.addHeader("X-Forwarded-Proto", forwardedProto);
        }
        return request;
    }

    private static MockHttpServletResponse invoke(
            SecurityHeadersConfig filter,
            MockHttpServletRequest request
    ) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> response.setStatus(200));
        return response;
    }
}
