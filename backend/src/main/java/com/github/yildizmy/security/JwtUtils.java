package com.github.yildizmy.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/** Issues and verifies short-lived access JWTs for this application only. */
@Slf4j
@Component
public class JwtUtils {

    private static final Duration ALLOWED_FUTURE_ISSUED_AT_SKEW = Duration.ofSeconds(30);

    private final SecretKey signingKey;
    private final long jwtExpirationMs;
    private final String jwtIssuer;
    private final String jwtAudience;
    private final Clock clock;

    @Autowired
    public JwtUtils(
            @Value("${app.security.jwtSecret}") String jwtSecret,
            @Value("${app.security.jwtExpirationMs}") long jwtExpirationMs,
            @Value("${app.security.jwtIssuer:e-wallet}") String jwtIssuer,
            @Value("${app.security.jwtAudience:e-wallet-storefront}") String jwtAudience
    ) {
        this(jwtSecret, jwtExpirationMs, jwtIssuer, jwtAudience, Clock.systemUTC());
    }

    JwtUtils(
            String jwtSecret,
            long jwtExpirationMs,
            String jwtIssuer,
            String jwtAudience,
            Clock clock
    ) {
        if (jwtExpirationMs <= 0) {
            throw new IllegalArgumentException("jwtExpirationMs must be positive");
        }
        this.jwtIssuer = requireText(jwtIssuer, "jwtIssuer");
        this.jwtAudience = requireText(jwtAudience, "jwtAudience");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.signingKey = Keys.hmacShaKeyFor(
                requireText(jwtSecret, "jwtSecret").getBytes(StandardCharsets.UTF_8)
        );
        this.jwtExpirationMs = jwtExpirationMs;
    }

    public AccessToken issueAccessToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusMillis(jwtExpirationMs);
        String value = Jwts.builder()
                .issuer(jwtIssuer)
                .audience().add(jwtAudience).and()
                .subject(userPrincipal.getUsername())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new AccessToken(value, expiresAt);
    }

    public String getUsernameFromJwtToken(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            parseClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            // Never log the bearer token or exception text because parser messages can echo claims.
            log.warn("JWT validation failed ({})", exception.getClass().getSimpleName());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(jwtIssuer)
                .requireAudience(jwtAudience)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        validateMandatoryClaims(claims);
        return claims;
    }

    private void validateMandatoryClaims(Claims claims) {
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new IllegalArgumentException("JWT subject is required");
        }
        Date issuedAt = claims.getIssuedAt();
        if (issuedAt == null) {
            throw new IllegalArgumentException("JWT issued-at time is required");
        }
        Date expiration = claims.getExpiration();
        if (expiration == null) {
            throw new IllegalArgumentException("JWT expiration is required");
        }
        if (!expiration.after(issuedAt)) {
            throw new IllegalArgumentException("JWT expiration must be after issued-at time");
        }
        Instant latestAcceptedIssuedAt = clock.instant().plus(ALLOWED_FUTURE_ISSUED_AT_SKEW);
        if (issuedAt.toInstant().isAfter(latestAcceptedIssuedAt)) {
            throw new IllegalArgumentException("JWT issued-at time is too far in the future");
        }
    }

    private static String requireText(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName + " is required").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    public record AccessToken(String value, Instant expiresAt) {

        public AccessToken {
            value = requireText(value, "value");
            Objects.requireNonNull(expiresAt, "expiresAt is required");
        }

        @Override
        public String toString() {
            return "AccessToken[value=<redacted>, expiresAt=" + expiresAt + ']';
        }
    }
}
