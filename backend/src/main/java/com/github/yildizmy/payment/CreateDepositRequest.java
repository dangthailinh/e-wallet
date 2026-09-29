package com.github.yildizmy.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateDepositRequest(
        @NotNull CreateDepositChannel channel,
        @Positive @Max(1_000_000_000L) long amountVnd
) {
}
