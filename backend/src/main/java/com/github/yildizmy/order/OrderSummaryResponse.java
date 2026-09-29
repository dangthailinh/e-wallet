package com.github.yildizmy.order;

import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID orderId,
        OrderStatus status,
        long totalVnd,
        Instant createdAt
) {
    static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(), order.getStatus(), order.getTotalVnd(), order.getCreatedAt()
        );
    }
}
