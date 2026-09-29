package com.github.yildizmy.catalog;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {

    private final CatalogCommandService commandService;

    public AdminCatalogController(CatalogCommandService commandService) {
        this.commandService = commandService;
    }

    @GetMapping("/categories")
    public AdminCatalogPageResponse<AdminCategoryResponse> listCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        return commandService.listCategories(page, size);
    }

    @GetMapping("/products")
    public AdminCatalogPageResponse<AdminProductResponse> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return commandService.listProducts(page, size);
    }

    @PostMapping(value = "/categories", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminCategoryResponse> createCategory(
            @Valid @RequestBody CategoryCommand command,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commandService.createCategory(command, authentication.getName()));
    }

    @PutMapping(value = "/categories/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminCategoryResponse updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryCommand command,
            Authentication authentication
    ) {
        return commandService.updateCategory(id, command, authentication.getName());
    }

    @PatchMapping(value = "/categories/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminCategoryResponse changeCategoryStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CatalogStatusCommand command,
            Authentication authentication
    ) {
        return commandService.changeCategoryStatus(id, command.status(), authentication.getName());
    }

    @PostMapping(value = "/products", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminProductResponse> createProduct(
            @Valid @RequestBody ProductCommand command,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commandService.createProduct(command, authentication.getName()));
    }

    @PutMapping(value = "/products/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminProductResponse updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductCommand command,
            Authentication authentication
    ) {
        return commandService.updateProduct(id, command, authentication.getName());
    }

    @PatchMapping(value = "/products/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminProductResponse changeProductStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CatalogStatusCommand command,
            Authentication authentication
    ) {
        return commandService.changeProductStatus(id, command.status(), authentication.getName());
    }
}
