package com.github.yildizmy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Adds the browser boundary headers at the application boundary. HSTS is emitted
 * only for HTTPS requests and only when explicitly enabled, which keeps local HTTP
 * development correct while allowing a TLS-terminating proxy to opt in safely.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@EnableConfigurationProperties(SecurityHeadersConfig.SecurityHeadersProperties.class)
public final class SecurityHeadersConfig extends OncePerRequestFilter {

    private static final String FORWARDED_PROTO = "X-Forwarded-Proto";

    private final SecurityHeadersProperties properties;

    public SecurityHeadersConfig(SecurityHeadersProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (properties.enabled()) {
            response.setHeader("Content-Security-Policy", properties.contentSecurityPolicy());
            response.setHeader("X-Frame-Options", properties.frameOptions());
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Referrer-Policy", properties.referrerPolicy());
            if (properties.hstsEnabled() && isSecureRequest(request)) {
                String hsts = "max-age=" + properties.hstsMaxAgeSeconds();
                if (properties.hstsIncludeSubDomains()) {
                    hsts += "; includeSubDomains";
                }
                if (properties.hstsPreload()) {
                    hsts += "; preload";
                }
                response.setHeader("Strict-Transport-Security", hsts);
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        if (!properties.trustForwardedProto()) {
            return false;
        }
        String forwardedProto = request.getHeader(FORWARDED_PROTO);
        if (!StringUtils.hasText(forwardedProto)) {
            return false;
        }
        return "https".equalsIgnoreCase(forwardedProto.split(",", 2)[0].trim());
    }

    @ConfigurationProperties(prefix = "app.security.headers")
    public record SecurityHeadersProperties(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'") String contentSecurityPolicy,
            @DefaultValue("DENY") String frameOptions,
            @DefaultValue("no-referrer") String referrerPolicy,
            @DefaultValue("false") boolean hstsEnabled,
            @DefaultValue("true") boolean hstsIncludeSubDomains,
            @DefaultValue("false") boolean hstsPreload,
            @DefaultValue("31536000") long hstsMaxAgeSeconds,
            @DefaultValue("false") boolean trustForwardedProto
    ) {
        private static final Set<String> FRAME_OPTIONS = Set.of("DENY", "SAMEORIGIN");
        private static final Set<String> REFERRER_POLICIES = Set.of(
                "no-referrer", "no-referrer-when-downgrade", "origin", "origin-when-cross-origin",
                "same-origin", "strict-origin", "strict-origin-when-cross-origin", "unsafe-url"
        );

        public SecurityHeadersProperties {
            contentSecurityPolicy = requireSafeHeaderValue(contentSecurityPolicy, "contentSecurityPolicy");
            frameOptions = canonicalAllowed(frameOptions, FRAME_OPTIONS, "frameOptions");
            referrerPolicy = canonicalAllowed(referrerPolicy, REFERRER_POLICIES, "referrerPolicy");
            if (hstsMaxAgeSeconds < 0) {
                throw new IllegalArgumentException("hstsMaxAgeSeconds must not be negative");
            }
            if (hstsPreload && (!hstsEnabled || !hstsIncludeSubDomains || hstsMaxAgeSeconds < 31_536_000L)) {
                throw new IllegalArgumentException("HSTS preload requires one year, includeSubDomains and enabled HSTS");
            }
        }

        private static String requireSafeHeaderValue(String value, String fieldName) {
            if (!StringUtils.hasText(value) || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new IllegalArgumentException(fieldName + " must be a non-empty single-line value");
            }
            return value.trim();
        }

        private static String canonicalAllowed(String value, Set<String> allowed, String fieldName) {
            String candidate = requireSafeHeaderValue(value, fieldName).toLowerCase(Locale.ROOT);
            return allowed.stream()
                    .filter(item -> item.toLowerCase(Locale.ROOT).equals(candidate))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(fieldName + " is not an allowed value"));
        }
    }
}
