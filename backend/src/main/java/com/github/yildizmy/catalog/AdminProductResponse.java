package com.github.yildizmy.catalog;

import java.time.Instant;
import java.util.UUID;

public record AdminProductResponse(
        UUID id,
        CategorySummary category,
        String name,
        String slug,
        String description,
        long priceVnd,
        CatalogStatus status,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public AdminProductResponse(
            UUID id,
            UUID categoryId,
            String categoryName,
            String categorySlug,
            String name,
            String slug,
            String description,
            long priceVnd,
            CatalogStatus status,
            String imageUrl,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        this(
                id,
                new CategorySummary(categoryId, categoryName, categorySlug),
                name,
                slug,
                description,
                priceVnd,
                status,
                imageUrl,
                createdAt,
                updatedAt,
                version
        );
    }

    static AdminProductResponse from(Product product) {
        Category category = product.getCategory();
        return new AdminProductResponse(
                product.getId(),
                category.getId(),
                category.getName(),
                category.getSlug(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getPriceVnd(),
                product.getStatus(),
                product.getImageUrl(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getVersion()
        );
    }

    public record CategorySummary(UUID id, String name, String slug) {
    }
}
