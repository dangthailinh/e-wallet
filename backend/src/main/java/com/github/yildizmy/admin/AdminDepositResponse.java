package com.github.yildizmy.admin;

import com.github.yildizmy.payment.Deposit;
import com.github.yildizmy.payment.DepositChannel;
import com.github.yildizmy.payment.DepositStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Allow-listed deposit data for reconciliation review. Merchant references,
 * provider transaction identifiers, and payment instructions are excluded.
 */
public record AdminDepositResponse(
        UUID id,
        long userId,
        DepositChannel channel,
        String providerCode,
        long amountVnd,
        String currency,
        DepositStatus status,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt,
        boolean needsReconciliationReview
) {

    public static AdminDepositResponse from(Deposit deposit) {
        return new AdminDepositResponse(
                deposit.getId(),
                deposit.getUser().getId(),
                deposit.getChannel(),
                deposit.getProviderCode(),
                deposit.getAmountVnd(),
                deposit.getCurrency(),
                deposit.getStatus(),
                deposit.getExpiresAt(),
                deposit.getPaidAt(),
                deposit.getCreatedAt(),
                deposit.getUpdatedAt(),
                deposit.isNeedsReconciliationReview()
        );
    }
}
