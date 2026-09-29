package com.github.yildizmy.payment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentPreparation(
        UUID depositId,
        String merchantReference,
        long amountVnd,
        String currency,
        Instant expiresAt
) {

    public PaymentPreparation {
        depositId = Objects.requireNonNull(depositId, "depositId is required");
        merchantReference = Objects.requireNonNull(merchantReference, "merchantReference is required");
        if (!merchantReference.matches("[A-Za-z0-9_-]{8,100}")) {
            throw new IllegalArgumentException("merchantReference has invalid format");
        }
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        if (!"VND".equals(currency)) {
            throw new IllegalArgumentException("currency must be VND");
        }
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
    }
}
