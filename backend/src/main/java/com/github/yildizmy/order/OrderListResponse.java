package com.github.yildizmy.order;

import org.springframework.data.domain.Page;

import java.util.List;

public record OrderListResponse(
        List<OrderSummaryResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
    static OrderListResponse from(Page<OrderSummaryResponse> result) {
        return new OrderListResponse(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()
        );
    }
}
