package com.github.yildizmy.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDetailResponse(
        UUID orderId,
        OrderStatus status,
        long totalVnd,
        Instant createdAt,
        List<OrderItemResponse> items
) {
    static OrderDetailResponse from(Order order, List<OrderItem> items) {
        return new OrderDetailResponse(
                order.getId(), order.getStatus(), order.getTotalVnd(), order.getCreatedAt(),
                items.stream().map(OrderItemResponse::from).toList()
        );
    }
}
