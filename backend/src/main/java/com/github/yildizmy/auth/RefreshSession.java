package com.github.yildizmy.auth;

import com.github.yildizmy.domain.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "refresh_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshSession {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)", updatable = false)
    @Convert(converter = RefreshTokenHashConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    private RefreshTokenHash tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_session_id", unique = true)
    private RefreshSession replacedBySession;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 64)
    private String revocationReason;

    @Version
    @Column(nullable = false)
    private Long version;

    RefreshSession(User user, UUID familyId, RefreshTokenHash tokenHash, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.user = requirePersistedUser(user);
        this.familyId = Objects.requireNonNull(familyId, "familyId is required");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public void rotateTo(RefreshSession replacement, Instant rotationTime) {
        requireActive();
        Instant validRotationTime = requireEventTime(rotationTime, "rotationTime");
        if (!validRotationTime.isAfter(createdAt)) {
            throw new IllegalArgumentException("rotationTime must be after createdAt");
        }
        RefreshSession validReplacement = requireReplacement(replacement, validRotationTime);
        if (!validRotationTime.isBefore(expiresAt)) {
            throw new IllegalArgumentException("rotationTime must be before expiresAt");
        }
        this.replacedBySession = validReplacement;
        this.rotatedAt = validRotationTime;
    }

    public void revoke(String reason, Instant revocationTime) {
        if (revokedAt != null) {
            return;
        }
        this.revocationReason = requireReason(reason);
        this.revokedAt = requireEventTime(revocationTime, "revocationTime");
    }

    private void requireActive() {
        if (rotatedAt != null || revokedAt != null) {
            throw new IllegalStateException("Refresh session is no longer active");
        }
    }

    private Instant requireEventTime(Instant value, String fieldName) {
        Instant eventTime = Objects.requireNonNull(value, fieldName + " is required");
        if (eventTime.isBefore(createdAt)) {
            throw new IllegalArgumentException(fieldName + " must not be before createdAt");
        }
        return eventTime;
    }

    private RefreshSession requireReplacement(RefreshSession replacement, Instant rotationTime) {
        RefreshSession successor = Objects.requireNonNull(replacement, "replacement is required");
        if (id.equals(successor.id)) {
            throw new IllegalArgumentException("A refresh session cannot replace itself");
        }
        if (!familyId.equals(successor.familyId) || !Objects.equals(user.getId(), successor.user.getId())) {
            throw new IllegalArgumentException("Replacement must belong to the same user and token family");
        }
        if (!successor.createdAt.equals(rotationTime)) {
            throw new IllegalArgumentException("Replacement must start at the rotation time");
        }
        successor.requireActive();
        return successor;
    }

    private static User requirePersistedUser(User value) {
        User user = Objects.requireNonNull(value, "user is required");
        if (user.getId() == null) {
            throw new IllegalArgumentException("user must already be persisted");
        }
        return user;
    }

    private static String requireReason(String value) {
        String reason = Objects.requireNonNull(value, "revocation reason is required").trim();
        if (reason.isEmpty() || reason.length() > 64) {
            throw new IllegalArgumentException("revocation reason must contain 1 to 64 characters");
        }
        return reason;
    }
}
