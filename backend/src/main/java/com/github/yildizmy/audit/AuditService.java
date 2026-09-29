package com.github.yildizmy.audit;

import com.github.yildizmy.admin.AdminPageResponse;
import com.github.yildizmy.admin.InvalidAdminOperationsQueryException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditService {

    private static final String CODE_PATTERN = "[A-Z][A-Z0-9_]{1,79}";
    private final AuditLogRepository auditLogs;

    public AdminPageResponse<AuditLogResponse> list(
            int page,
            int size,
            Long actorUserId,
            String action,
            String resourceType,
            UUID resourceId
    ) {
        validate(page, size, actorUserId);
        String safeAction = normalizeCode(action);
        String safeResourceType = normalizeCode(resourceType);
        return AdminPageResponse.from(auditLogs.findForAdmin(
                actorUserId,
                safeAction,
                safeResourceType,
                resourceId,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.desc("createdAt"), Sort.Order.desc("id")
                ))
        ).map(AuditLogResponse::from));
    }

    @Transactional
    public AuditLogResponse record(
            long actorUserId,
            String action,
            String resourceType,
            UUID resourceId,
            UUID parentResourceId,
            String requestId
    ) {
        return AuditLogResponse.from(auditLogs.saveAndFlush(
                AuditLog.record(actorUserId, action, resourceType, resourceId, parentResourceId, requestId)
        ));
    }

    private static void validate(int page, int size, Long actorUserId) {
        if (page < 0 || page > 10_000 || size < 1 || size > 100
                || actorUserId != null && actorUserId < 1) {
            throw new InvalidAdminOperationsQueryException();
        }
    }

    private static String normalizeCode(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches(CODE_PATTERN)) {
            throw new InvalidAdminOperationsQueryException();
        }
        return normalized;
    }
}
