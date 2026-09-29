package com.github.yildizmy.admin;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable pagination envelope shared by admin investigation endpoints.
 * Item records intentionally define the allow-listed response fields.
 */
public record AdminPageResponse<T>(
        List<T> items,
        PageMetadata page
) {

    public AdminPageResponse {
        items = List.copyOf(items);
    }

    public static <T> AdminPageResponse<T> from(Page<T> result) {
        return new AdminPageResponse<>(
                result.getContent(),
                new PageMetadata(
                        result.getNumber(),
                        result.getSize(),
                        result.getTotalElements(),
                        result.getTotalPages(),
                        result.hasNext(),
                        result.hasPrevious()
                )
        );
    }

    public record PageMetadata(
            int number,
            int size,
            long totalItems,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious
    ) {
    }
}
