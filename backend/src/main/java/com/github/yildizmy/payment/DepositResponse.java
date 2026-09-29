package com.github.yildizmy.payment;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DepositResponse(
        UUID id,
        DepositChannel channel,
        String providerCode,
        String merchantReference,
        long amountVnd,
        String currency,
        DepositStatus status,
        Instant expiresAt,
        Instant paidAt,
        PaymentInstructions instructions
) {

    static DepositResponse created(Deposit deposit, PaymentInstructions instructions) {
        return from(deposit, instructions);
    }

    static DepositResponse status(Deposit deposit) {
        return from(deposit, null);
    }

    private static DepositResponse from(Deposit deposit, PaymentInstructions instructions) {
        return new DepositResponse(
                deposit.getId(),
                deposit.getChannel(),
                deposit.getProviderCode(),
                deposit.getMerchantReference(),
                deposit.getAmountVnd(),
                deposit.getCurrency(),
                deposit.getStatus(),
                deposit.getExpiresAt(),
                deposit.getPaidAt(),
                instructions
        );
    }
}
