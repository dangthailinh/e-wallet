package com.github.yildizmy.payment;

import java.time.Instant;
import java.util.UUID;

/** Result of an explicitly requested, audited provider status query. */
public record ProviderRequeryResult(
        UUID depositId,
        boolean providerQueried,
        String outcome,
        boolean mismatch,
        boolean needsReconciliationReview,
        String reason,
        Instant checkedAt
) {
}
