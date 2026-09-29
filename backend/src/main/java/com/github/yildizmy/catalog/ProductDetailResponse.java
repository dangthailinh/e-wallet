package com.github.yildizmy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Public details for one digital product")
public record ProductDetailResponse(
        @Schema(example = "ad3a7917-196d-4f6e-9235-03f36bca7caf") UUID id,
        CategorySummary category,
        @Schema(example = "ChatGPT Plus") String name,
        @Schema(example = "chatgpt-plus") String slug,
        @Schema(example = "Digital account delivered after payment.") String description,
        @Schema(example = "150000", description = "Positive whole Vietnamese dong") long priceVnd,
        @Schema(example = "https://cdn.example.com/chatgpt-plus.png", nullable = true) String imageUrl,
        @Schema(example = "4", description = "Sellable inventory items") long availableCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProductDetailResponse from(ProductView product) {
        return new ProductDetailResponse(
                product.id(),
                new CategorySummary(product.categoryId(), product.categoryName(), product.categorySlug()),
                product.name(),
                product.slug(),
                product.description(),
                product.priceVnd(),
                product.imageUrl(),
                product.availableCount(),
                product.createdAt(),
                product.updatedAt()
        );
    }

    public record CategorySummary(
            @Schema(example = "fa560a3d-660c-4e91-a7a9-5c5553fb4b87") UUID id,
            @Schema(example = "Artificial intelligence") String name,
            @Schema(example = "ai") String slug
    ) {
    }
}
