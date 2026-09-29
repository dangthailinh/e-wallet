package com.github.yildizmy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "app.security.cookies")
public record AuthCookieProperties(
        @DefaultValue("EW_ACCESS") String accessName,
        @DefaultValue("EW_REFRESH") String refreshName,
        @DefaultValue("false") boolean secure,
        @DefaultValue("Lax") String sameSite,
        @DefaultValue("/api/v1") String accessPath,
        @DefaultValue("/api/v1/auth") String refreshPath,
        @DefaultValue("") String domain
) {

    private static final Set<String> SAME_SITE_VALUES = Set.of("Strict", "Lax", "None");

    public AuthCookieProperties {
        accessName = requireCookieName(accessName, "accessName");
        refreshName = requireCookieName(refreshName, "refreshName");
        if (accessName.equals(refreshName)) {
            throw new IllegalArgumentException("Access and refresh cookie names must differ");
        }
        sameSite = canonicalSameSite(sameSite);
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalArgumentException("SameSite=None cookies must be Secure");
        }
        accessPath = requirePath(accessPath, "accessPath");
        refreshPath = requirePath(refreshPath, "refreshPath");
        domain = Objects.requireNonNull(domain, "domain is required").trim();
    }

    public ResponseCookie accessCookie(String value, Duration maxAge) {
        return build(accessName, value, accessPath, maxAge);
    }

    public ResponseCookie refreshCookie(String value, Duration maxAge) {
        return build(refreshName, value, refreshPath, maxAge);
    }

    public ResponseCookie clearAccessCookie() {
        return build(accessName, "", accessPath, Duration.ZERO);
    }

    public ResponseCookie clearRefreshCookie() {
        return build(refreshName, "", refreshPath, Duration.ZERO);
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, Objects.requireNonNull(value, "value is required"))
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(requireNonNegative(maxAge));
        if (!domain.isEmpty()) {
            builder.domain(domain);
        }
        return builder.build();
    }

    private static Duration requireNonNegative(Duration value) {
        Duration duration = Objects.requireNonNull(value, "maxAge is required");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("maxAge must not be negative");
        }
        return duration;
    }

    private static String requireCookieName(String value, String fieldName) {
        String name = Objects.requireNonNull(value, fieldName + " is required").trim();
        if (name.isEmpty() || name.chars().anyMatch(character -> Character.isWhitespace(character) || character == ';' || character == ',')) {
            throw new IllegalArgumentException(fieldName + " is not a valid cookie name");
        }
        return name;
    }

    private static String requirePath(String value, String fieldName) {
        String path = Objects.requireNonNull(value, fieldName + " is required").trim();
        if (!path.startsWith("/") || path.contains(";")) {
            throw new IllegalArgumentException(fieldName + " must be an absolute cookie path");
        }
        return path;
    }

    private static String canonicalSameSite(String value) {
        String candidate = Objects.requireNonNull(value, "sameSite is required").trim().toLowerCase(Locale.ROOT);
        return SAME_SITE_VALUES.stream()
                .filter(allowed -> allowed.toLowerCase(Locale.ROOT).equals(candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("sameSite must be Strict, Lax or None"));
    }
}
