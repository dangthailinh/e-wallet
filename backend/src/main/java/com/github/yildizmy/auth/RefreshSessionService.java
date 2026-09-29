package com.github.yildizmy.auth;

import com.github.yildizmy.domain.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RefreshSessionService {

    private static final int TOKEN_BYTES = 32;
    private static final String REUSE_REASON = "refresh-token-reuse";
    private static final String LOGOUT_REASON = "logout";
    private static final String INVALID_TOKEN_MESSAGE = "Refresh token is invalid";

    private final RefreshSessionRepository repository;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public RefreshSessionService(
            RefreshSessionRepository repository,
            @Value("${app.security.refreshTokenTtl:P30D}") Duration refreshTokenTtl
    ) {
        this(repository, refreshTokenTtl, new SecureRandom(), Clock.systemUTC());
    }

    RefreshSessionService(
            RefreshSessionRepository repository,
            Duration refreshTokenTtl,
            SecureRandom secureRandom,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.refreshTokenTtl = requirePositive(refreshTokenTtl);
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Transactional
    public RefreshToken issue(User user) {
        Instant issuedAt = now();
        return createAndPersist(user, UUID.randomUUID(), issuedAt);
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = BadCredentialsException.class
    )
    public RefreshToken rotate(String rawToken) {
        RefreshTokenHash presentedHash = RefreshTokenHash.fromToken(rawToken);
        Instant observedAt = now();
        RefreshSession root = repository.findFamilyRootByTokenHashForUpdate(presentedHash)
                .orElseThrow(RefreshSessionService::invalidToken);
        List<RefreshSession> family = repository.findFamilyByFamilyIdForUpdate(root.getFamilyId());
        RefreshSession presented = family.stream()
                .filter(session -> session.getTokenHash().equals(presentedHash))
                .findFirst()
                .orElseThrow(RefreshSessionService::invalidToken);

        if (isActiveAt(presented, observedAt)) {
            return rotateActive(presented, observedAt);
        }
        return rejectConsumedOrInvalid(presented, family, observedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revoke(String rawToken) {
        RefreshTokenHash presentedHash = RefreshTokenHash.fromToken(rawToken);
        repository.findFamilyRootByTokenHashForUpdate(presentedHash).ifPresent(root -> {
            List<RefreshSession> family = repository.findFamilyByFamilyIdForUpdate(root.getFamilyId());
            Instant observedAt = now();
            Instant revocationTime = family.stream()
                    .map(RefreshSession::getCreatedAt)
                    .filter(createdAt -> createdAt.isAfter(observedAt))
                    .max(Instant::compareTo)
                    .orElse(observedAt);
            family.forEach(session -> session.revoke(LOGOUT_REASON, revocationTime));
        });
    }

    private RefreshToken rotateActive(RefreshSession current, Instant observedAt) {
        Instant rotationTime = observedAt.isAfter(current.getCreatedAt())
                ? observedAt
                : current.getCreatedAt().plus(1, ChronoUnit.MICROS);
        String rawSuccessor = generateOpaqueToken();
        RefreshSession successor = new RefreshSession(
                current.getUser(),
                current.getFamilyId(),
                RefreshTokenHash.fromToken(rawSuccessor),
                rotationTime,
                rotationTime.plus(refreshTokenTtl)
        );
        repository.save(successor);
        current.rotateTo(successor, rotationTime);
        return new RefreshToken(rawSuccessor, successor.getExpiresAt(), successor.getUser().getId());
    }

    private RefreshToken rejectConsumedOrInvalid(
            RefreshSession presented,
            List<RefreshSession> family,
            Instant observedAt
    ) {
        boolean consumed = presented.getRotatedAt() != null || presented.getRevokedAt() != null;
        if (consumed) {
            Instant revocationTime = family.stream()
                    .map(RefreshSession::getCreatedAt)
                    .filter(createdAt -> createdAt.isAfter(observedAt))
                    .max(Instant::compareTo)
                    .orElse(observedAt);
            family.forEach(session -> session.revoke(REUSE_REASON, revocationTime));
        }
        throw invalidToken();
    }

    private static boolean isActiveAt(RefreshSession session, Instant observedAt) {
        return !session.getCreatedAt().isAfter(observedAt)
                && session.getExpiresAt().isAfter(observedAt)
                && session.getRotatedAt() == null
                && session.getRevokedAt() == null;
    }

    private RefreshToken createAndPersist(User user, UUID familyId, Instant issuedAt) {
        String rawToken = generateOpaqueToken();
        RefreshSession session = new RefreshSession(
                user,
                familyId,
                RefreshTokenHash.fromToken(rawToken),
                issuedAt,
                issuedAt.plus(refreshTokenTtl)
        );
        repository.save(session);
        return new RefreshToken(rawToken, session.getExpiresAt(), session.getUser().getId());
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static Duration requirePositive(Duration value) {
        Duration duration = Objects.requireNonNull(value, "refreshTokenTtl is required");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("refreshTokenTtl must be positive");
        }
        return duration;
    }

    private static BadCredentialsException invalidToken() {
        return new BadCredentialsException(INVALID_TOKEN_MESSAGE);
    }

    public record RefreshToken(String value, Instant expiresAt, Long userId) {

        public RefreshToken {
            Objects.requireNonNull(value, "value is required");
            Objects.requireNonNull(expiresAt, "expiresAt is required");
            Objects.requireNonNull(userId, "userId is required");
        }

        @Override
        public String toString() {
            return "RefreshToken[value=<redacted>, expiresAt=" + expiresAt + ", userId=" + userId + ']';
        }
    }
}
