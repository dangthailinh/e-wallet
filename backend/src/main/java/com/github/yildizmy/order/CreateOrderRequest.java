package com.github.yildizmy.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateOrderRequest(@NotNull UUID productId, @Positive long expectedPriceVnd) {
}
