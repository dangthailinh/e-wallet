package com.github.yildizmy.payment;

import com.github.yildizmy.admin.AdminPageResponse;
import com.github.yildizmy.config.RequestIdFilter;
import com.github.yildizmy.security.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping(value = "/api/v1/admin/reconciliation", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReconciliationController {

    private final ReconciliationService reconciliation;

    @GetMapping("/deposits")
    public ResponseEntity<AdminPageResponse<ReconciliationResult>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reconciliation.list(page, size));
    }

    @PostMapping("/deposits/{depositId}/requery")
    public ResponseEntity<ProviderRequeryResult> requery(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID depositId,
            HttpServletRequest request
    ) {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reconciliation.requery(currentUser.getId(), depositId, requestId));
    }
}
