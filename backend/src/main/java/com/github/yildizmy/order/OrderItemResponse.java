package com.github.yildizmy.order;

import java.util.UUID;

public record OrderItemResponse(
        UUID itemId,
        UUID productId,
        String productName,
        long unitPriceVnd
) {
    static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(), item.getProduct().getId(), item.getProductName(), item.getUnitPriceVnd()
        );
    }
}
