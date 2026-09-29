package com.github.yildizmy.catalog;

import java.time.Instant;
import java.util.UUID;

public record AdminCategoryResponse(
        UUID id,
        String name,
        String slug,
        int displayOrder,
        CatalogStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    static AdminCategoryResponse from(Category category) {
        return new AdminCategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDisplayOrder(),
                category.getStatus(),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                category.getVersion()
        );
    }
}
