package com.github.yildizmy.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
@Entity
@Table(name = "audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    public static final String SECRET_REVEALED = "SECRET_REVEALED";
    public static final String ORDER_ITEM = "ORDER_ITEM";

    private static final Pattern REQUEST_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{7,127}");
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,79}");

    @Id
    private UUID id;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private long actorUserId;

    @Column(nullable = false, updatable = false, length = 80)
    private String action;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 80)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "parent_resource_id", updatable = false)
    private UUID parentResourceId;

    @Column(name = "request_id", nullable = false, updatable = false, length = 128)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AuditLog secretRevealed(
            long actorUserId, UUID orderId, UUID orderItemId, String requestId
    ) {
        return record(actorUserId, SECRET_REVEALED, ORDER_ITEM, orderItemId, orderId, requestId);
    }

    /**
     * Builds an append-only audit record from already-sanitized identifiers. The
     * audit table deliberately stores no request body, token, provider signature,
     * or inventory plaintext; callers can only supply bounded event codes and UUIDs.
     */
    public static AuditLog record(
            long actorUserId,
            String action,
            String resourceType,
            UUID resourceId,
            UUID parentResourceId,
            String requestId
    ) {
        if (actorUserId <= 0) {
            throw new IllegalArgumentException("actorUserId must be positive");
        }
        String safeAction = requireCode(action, "action");
        String safeResourceType = requireCode(resourceType, "resourceType");
        String safeRequestId = Objects.requireNonNull(requestId, "requestId is required");
        if (!REQUEST_ID.matcher(safeRequestId).matches()) {
            throw new IllegalArgumentException("requestId has invalid format");
        }

        AuditLog event = new AuditLog();
        event.id = UUID.randomUUID();
        event.actorUserId = actorUserId;
        event.action = safeAction;
        event.resourceType = safeResourceType;
        event.resourceId = Objects.requireNonNull(resourceId, "resourceId is required");
        event.parentResourceId = parentResourceId;
        event.requestId = safeRequestId;
        event.createdAt = Instant.now();
        return event;
    }

    private static String requireCode(String value, String fieldName) {
        String code = Objects.requireNonNull(value, fieldName + " is required");
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException(fieldName + " has invalid format");
        }
        return code;
    }
}
