package com.github.yildizmy.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Public administrative projection of an audit event. Metadata is intentionally
 * absent: audit rows must remain useful for investigation without becoming a
 * second channel for secrets or provider payloads.
 */
public record AuditLogResponse(
        UUID id,
        long actorUserId,
        String action,
        String resourceType,
        UUID resourceId,
        UUID parentResourceId,
        String requestId,
        Instant createdAt
) {

    public static AuditLogResponse from(AuditLog event) {
        return new AuditLogResponse(
                event.getId(),
                event.getActorUserId(),
                event.getAction(),
                event.getResourceType(),
                event.getResourceId(),
                event.getParentResourceId(),
                event.getRequestId(),
                event.getCreatedAt()
        );
    }
}
