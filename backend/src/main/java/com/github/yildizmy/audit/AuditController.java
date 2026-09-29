package com.github.yildizmy.audit;

import com.github.yildizmy.admin.AdminPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping(value = "/api/v1/admin/audit-logs", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<AdminPageResponse<AuditLogResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) UUID resourceId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(auditService.list(page, size, actorId, action, resourceType, resourceId));
    }
}
