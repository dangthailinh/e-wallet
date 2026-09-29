package com.github.yildizmy.inventory;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryController {

    private final InventoryImportService importService;
    private final InventoryAdminService adminService;

    public AdminInventoryController(InventoryImportService importService, InventoryAdminService adminService) {
        this.importService = importService;
        this.adminService = adminService;
    }

    @PostMapping(value = "/products/{productId}/inventory-items", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InventoryImportResponse> importInventory(
            @PathVariable UUID productId,
            @Valid @RequestBody InventoryImportRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Cache-Control", "no-store")
                .body(importService.importBatch(productId, request, authentication.getName()));
    }

    @GetMapping("/products/{productId}/inventory-items")
    public ResponseEntity<AdminInventoryListResponse> listInventory(@PathVariable UUID productId) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(adminService.list(productId));
    }

    @PatchMapping(value = "/inventory-items/{itemId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminInventoryItemResponse> changeStatus(
            @PathVariable UUID itemId,
            @Valid @RequestBody InventoryStatusCommand command,
            Authentication authentication
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(adminService.changeStatus(itemId, command.status(), authentication.getName()));
    }
}
