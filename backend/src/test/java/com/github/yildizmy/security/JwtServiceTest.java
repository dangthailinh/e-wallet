package com.github.yildizmy.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "e-wallet";
    private static final String AUDIENCE = "e-wallet-storefront";
    private static final Instant NOW = Instant.parse("2026-09-17T09:00:00Z");

    private JwtUtils jwtUtils;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils(
                SECRET,
                60_000,
                ISSUER,
                AUDIENCE,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        var principal = new UserDetailsImpl(1L, "buyer", "ignored", "Test", "Buyer", List.of());
        authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void issuesAndValidatesAllRequiredAccessTokenClaims() {
        String token = jwtUtils.issueAccessToken(authentication).value();

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(jwtUtils.validateJwtToken(token)).isTrue();
        assertThat(jwtUtils.getUsernameFromJwtToken(token)).isEqualTo("buyer");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getAudience()).containsExactly(AUDIENCE);
        assertThat(claims.getSubject()).isEqualTo("buyer");
        assertThat(claims.getIssuedAt()).isEqualTo(Date.from(NOW));
        assertThat(claims.getExpiration()).isEqualTo(Date.from(NOW.plusSeconds(60)));
    }

    @Test
    void rejectsATokenForAnotherAudience() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .audience().add("another-service").and()
                .subject("buyer")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtUtils.validateJwtToken(token)).isFalse();
        assertThatThrownBy(() -> jwtUtils.getUsernameFromJwtToken(token))
                .isInstanceOf(IncorrectClaimException.class);
    }

    @Test
    void rejectsSignedTokensWithMissingOrInvalidMandatoryClaims() {
        var signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String missingSubject = Jwts.builder()
                .issuer(ISSUER).audience().add(AUDIENCE).and()
                .issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(60)))
                .signWith(signingKey).compact();
        String missingIssuedAt = Jwts.builder()
                .issuer(ISSUER).audience().add(AUDIENCE).and()
                .subject("buyer").expiration(Date.from(NOW.plusSeconds(60)))
                .signWith(signingKey).compact();
        String missingExpiration = Jwts.builder()
                .issuer(ISSUER).audience().add(AUDIENCE).and()
                .subject("buyer").issuedAt(Date.from(NOW))
                .signWith(signingKey).compact();
        String futureIssuedAt = Jwts.builder()
                .issuer(ISSUER).audience().add(AUDIENCE).and()
                .subject("buyer")
                .issuedAt(Date.from(NOW.plusSeconds(31)))
                .expiration(Date.from(NOW.plusSeconds(91)))
                .signWith(signingKey).compact();

        assertThat(jwtUtils.validateJwtToken(missingSubject)).isFalse();
        assertThat(jwtUtils.validateJwtToken(missingIssuedAt)).isFalse();
        assertThat(jwtUtils.validateJwtToken(missingExpiration)).isFalse();
        assertThat(jwtUtils.validateJwtToken(futureIssuedAt)).isFalse();
        assertThatThrownBy(() -> jwtUtils.getUsernameFromJwtToken(missingExpiration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT expiration is required");
    }

    @Test
    void rejectsAWeakConfiguredSigningKeyAtConstructionTime() {
        assertThatThrownBy(() -> new JwtUtils(
                "too-short",
                60_000,
                ISSUER,
                AUDIENCE,
                Clock.fixed(NOW, ZoneOffset.UTC)
        )).isInstanceOf(WeakKeyException.class);
    }
}
