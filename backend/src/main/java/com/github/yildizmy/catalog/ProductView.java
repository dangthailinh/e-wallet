package com.github.yildizmy.catalog;

import java.time.Instant;
import java.util.UUID;

public record ProductView(
        UUID id,
        UUID categoryId,
        String categoryName,
        String categorySlug,
        String name,
        String slug,
        String description,
        long priceVnd,
        String imageUrl,
        long availableCount,
        Instant createdAt,
        Instant updatedAt
) {
}
