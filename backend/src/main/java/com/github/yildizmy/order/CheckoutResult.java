package com.github.yildizmy.order;

import java.time.Instant;
import java.util.UUID;

public record CheckoutResult(
        UUID orderId,
        UUID orderItemId,
        UUID productId,
        String productName,
        long totalVnd,
        OrderStatus status,
        Instant createdAt,
        long walletBalanceVnd,
        boolean created
) {
    static CheckoutResult from(Order order, OrderItem item, long walletBalanceVnd, boolean created) {
        return new CheckoutResult(
                order.getId(),
                item.getId(),
                item.getProduct().getId(),
                item.getProductName(),
                order.getTotalVnd(),
                order.getStatus(),
                order.getCreatedAt(),
                walletBalanceVnd,
                created
        );
    }
}
