package com.github.yildizmy.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Sanitized reconciliation evidence. Provider transaction/event identities and
 * merchant references are deliberately not exposed by this projection.
 */
public record ReconciliationResult(
        UUID depositId,
        DepositStatus depositStatus,
        long expectedAmountVnd,
        Long ledgerAmountVnd,
        boolean ledgerEntryPresent,
        boolean mismatch,
        boolean needsReconciliationReview,
        String reason,
        Instant checkedAt
) {
}
