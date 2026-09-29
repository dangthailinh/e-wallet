package com.github.yildizmy.payment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PaymentProvider {

    DepositChannel channel();

    String providerCode();

    default void validateAmount(long amountVnd, String currency) {
        // Providers may narrow the platform-wide deposit limits before anything is persisted.
    }

    PaymentInstructions prepare(PaymentPreparation payment);

    /**
     * Provider status query used only by an authorized reconciliation operator.
     * Empty means this adapter intentionally does not support querying. Adapters
     * must not mutate the wallet or deposit while answering this request.
     */
    default Optional<VerifiedNotification> requery(PaymentPreparation payment) {
        return Optional.empty();
    }

    VerifiedNotification verify(byte[] rawBody, Map<String, List<String>> headers);
}
