package com.github.yildizmy.admin;

import com.github.yildizmy.order.Order;
import com.github.yildizmy.order.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Allow-listed order data for operational review. Product names and inventory
 * payloads are deliberately excluded from this administrative list response.
 */
public record AdminOrderResponse(
        UUID orderId,
        long userId,
        OrderStatus status,
        long totalVnd,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminOrderResponse from(Order order) {
        return new AdminOrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getStatus(),
                order.getTotalVnd(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
