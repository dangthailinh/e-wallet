package com.github.yildizmy.order;

import java.time.Instant;
import java.util.UUID;

public record OrderCheckoutResponse(
        UUID orderId,
        UUID itemId,
        UUID productId,
        String productName,
        long totalVnd,
        OrderStatus status,
        Instant createdAt,
        long walletBalanceVnd
) {
    static OrderCheckoutResponse from(CheckoutResult result) {
        return new OrderCheckoutResponse(
                result.orderId(), result.orderItemId(), result.productId(), result.productName(),
                result.totalVnd(), result.status(), result.createdAt(), result.walletBalanceVnd()
        );
    }
}
