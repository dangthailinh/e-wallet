package com.github.yildizmy.auth;

import com.github.yildizmy.domain.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.lang.reflect.Modifier;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshSessionTest {

    @Test
    void keepsDigestReconstructionAndSessionCreationInsideTheAuthBoundary() throws NoSuchMethodException {
        assertThat(Modifier.isPublic(RefreshTokenHashConverter.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(RefreshSession.class.getDeclaredConstructor(
                User.class,
                UUID.class,
                RefreshTokenHash.class,
                Instant.class,
                Instant.class
        ).getModifiers())).isFalse();
    }

    @Test
    void hashesEvenTokenValuesThatAlreadyLookLikeSha256() {
        String rawToken = "a".repeat(64);

        RefreshTokenHash hash = RefreshTokenHash.fromToken(rawToken);

        assertThat(hash.value()).hasSize(64).isNotEqualTo(rawToken);
        assertThat(hash).hasToString("RefreshTokenHash[redacted]");
    }

    @Test
    void rejectsRotationAtOrAfterExpiry() {
        User user = user(1L);
        UUID familyId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-17T08:00:00Z");
        Instant expiresAt = createdAt.plus(1, ChronoUnit.DAYS);
        RefreshSession current = session(user, familyId, "current", createdAt, expiresAt);
        RefreshSession replacement = session(user, familyId, "replacement", expiresAt, expiresAt.plus(1, ChronoUnit.DAYS));

        assertThatThrownBy(() -> current.rotateTo(replacement, expiresAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rotationTime must be before expiresAt");
    }

    @Test
    void rejectsRotationWithoutStrictTemporalProgress() {
        User user = user(1L);
        UUID familyId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-17T08:00:00Z");
        RefreshSession current = session(user, familyId, "current", createdAt, createdAt.plus(1, ChronoUnit.DAYS));
        RefreshSession replacement = session(
                user,
                familyId,
                "replacement",
                createdAt,
                createdAt.plus(1, ChronoUnit.DAYS)
        );

        assertThatThrownBy(() -> current.rotateTo(replacement, createdAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rotationTime must be after createdAt");
    }

    @Test
    void rejectsReplacementOutsideTheCurrentUserAndFamily() {
        Instant now = Instant.parse("2026-09-17T08:00:00Z");
        User user = user(1L);
        RefreshSession current = session(user, UUID.randomUUID(), "current", now, now.plus(1, ChronoUnit.DAYS));
        RefreshSession otherFamily = session(user, UUID.randomUUID(), "other", now, now.plus(1, ChronoUnit.DAYS));

        assertThatThrownBy(() -> current.rotateTo(otherFamily, now.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Replacement must belong to the same user and token family");
        assertThatThrownBy(() -> current.rotateTo(current, now.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A refresh session cannot replace itself");
    }

    @Test
    void rejectsReplacementThatDoesNotStartAtRotationTime() {
        Instant now = Instant.parse("2026-09-17T08:00:00Z");
        User user = user(1L);
        UUID familyId = UUID.randomUUID();
        RefreshSession current = session(user, familyId, "current", now, now.plus(1, ChronoUnit.DAYS));
        RefreshSession replacement = session(
                user,
                familyId,
                "replacement",
                now.plusSeconds(2),
                now.plus(1, ChronoUnit.DAYS)
        );

        assertThatThrownBy(() -> current.rotateTo(replacement, now.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Replacement must start at the rotation time");
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static RefreshSession session(
            User user,
            UUID familyId,
            String rawToken,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new RefreshSession(
                user,
                familyId,
                RefreshTokenHash.fromToken(rawToken),
                createdAt,
                expiresAt
        );
    }
}
