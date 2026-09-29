package com.github.yildizmy.catalog;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminCatalogPageResponse<T>(
        List<T> items,
        ProductListResponse.PageMetadata page
) {
    static <T> AdminCatalogPageResponse<T> from(Page<T> result) {
        return new AdminCatalogPageResponse<>(
                List.copyOf(result.getContent()),
                new ProductListResponse.PageMetadata(
                        result.getNumber(),
                        result.getSize(),
                        result.getTotalElements(),
                        result.getTotalPages(),
                        result.hasNext(),
                        result.hasPrevious()
                )
        );
    }
}
