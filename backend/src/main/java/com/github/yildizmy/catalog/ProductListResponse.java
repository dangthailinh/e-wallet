package com.github.yildizmy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

@Schema(description = "A page of public digital products")
public record ProductListResponse(
        List<Item> items,
        PageMetadata page
) {

    public static ProductListResponse from(Page<ProductView> products) {
        List<Item> items = products.getContent().stream().map(Item::from).toList();
        return new ProductListResponse(
                items,
                new PageMetadata(
                        products.getNumber(),
                        products.getSize(),
                        products.getTotalElements(),
                        products.getTotalPages(),
                        products.hasNext(),
                        products.hasPrevious()
                )
        );
    }

    public record Item(
            @Schema(example = "ad3a7917-196d-4f6e-9235-03f36bca7caf") UUID id,
            CategorySummary category,
            @Schema(example = "ChatGPT Plus") String name,
            @Schema(example = "chatgpt-plus") String slug,
            @Schema(example = "150000", description = "Positive whole Vietnamese dong") long priceVnd,
            @Schema(example = "https://cdn.example.com/chatgpt-plus.png", nullable = true) String imageUrl,
            @Schema(example = "4", description = "Sellable inventory items") long availableCount
    ) {

        private static Item from(ProductView product) {
            return new Item(
                    product.id(),
                    new CategorySummary(product.categoryId(), product.categoryName(), product.categorySlug()),
                    product.name(),
                    product.slug(),
                    product.priceVnd(),
                    product.imageUrl(),
                    product.availableCount()
            );
        }
    }

    public record CategorySummary(
            @Schema(example = "fa560a3d-660c-4e91-a7a9-5c5553fb4b87") UUID id,
            @Schema(example = "Artificial intelligence") String name,
            @Schema(example = "ai") String slug
    ) {
    }

    public record PageMetadata(
            @Schema(example = "0", description = "Zero-based page number") int number,
            @Schema(example = "20") int size,
            @Schema(example = "45") long totalItems,
            @Schema(example = "3") int totalPages,
            @Schema(example = "true") boolean hasNext,
            @Schema(example = "false") boolean hasPrevious
    ) {
    }
}
