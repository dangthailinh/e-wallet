package com.github.yildizmy.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping(value = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminOperationsController {

    private final AdminOperationsQueryService queryService;

    @GetMapping("/orders")
    public ResponseEntity<AdminPageResponse<AdminOrderResponse>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queryService.listOrders(page, size, status, userId));
    }

    @GetMapping("/deposits")
    public ResponseEntity<AdminPageResponse<AdminDepositResponse>> listDeposits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long userId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queryService.listDeposits(page, size, status, channel, userId));
    }
}
