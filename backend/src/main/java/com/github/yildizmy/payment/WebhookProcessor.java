package com.github.yildizmy.payment;

import com.github.yildizmy.config.BusinessMetrics;
import com.github.yildizmy.wallet.WalletEntryType;
import com.github.yildizmy.wallet.WalletLedgerService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Service
public class WebhookProcessor {

    private final DepositRepository deposits;
    private final WebhookEventRepository events;
    private final WalletLedgerService ledger;
    private final ObjectProvider<BusinessMetrics> metrics;

    public WebhookProcessor(
            DepositRepository deposits,
            WebhookEventRepository events,
            WalletLedgerService ledger,
            ObjectProvider<BusinessMetrics> metrics
    ) {
        this.deposits = deposits;
        this.events = events;
        this.ledger = ledger;
        this.metrics = metrics;
    }

    @Transactional
    public Result process(VerifiedNotification notification, byte[] requestHash) {
        VerifiedNotification verified = Objects.requireNonNull(notification, "notification is required");
        byte[] digest = requireDigest(requestHash);
        UUID candidateEventId = UUID.randomUUID();
        int inserted = events.insertReceived(
                candidateEventId,
                verified.providerCode(),
                verified.providerEventId(),
                verified.providerTransactionId(),
                digest
        );

        if (inserted == 0) {
            metrics.ifAvailable(BusinessMetrics::webhookDuplicate);
            return replayExisting(verified, digest);
        }

        WebhookEvent event = events.findById(candidateEventId)
                .orElseThrow(() -> new IllegalStateException("Inserted webhook event could not be loaded"));
        Deposit deposit = deposits.lockByProviderAndReference(
                        verified.providerCode(), verified.merchantReference()
                )
                .orElse(null);

        if (deposit == null) {
            event.markForReview("DEPOSIT_NOT_FOUND");
            return result(event, null, true);
        }

        event.attachTo(deposit);

        if (deposit.getStatus() != DepositStatus.PENDING
                && deposit.getStatus() != DepositStatus.REVIEW) {
            event.markForReview("DEPOSIT_NOT_PENDING");
            deposit.flagForReconciliationReview();
            return result(event, deposit, true);
        }

        if (!paymentTermsMatch(deposit, verified)) {
            if (deposit.getStatus() == DepositStatus.PENDING) {
                deposit.markForReview();
            }
            event.markForReview("PAYMENT_TERMS_MISMATCH");
            return result(event, deposit, true);
        }

        if (verified.outcome() == VerifiedNotification.Outcome.FAILED) {
            deposit.markFailed();
            event.markIgnored("PAYMENT_FAILED");
            return result(event, deposit, true);
        }

        WalletLedgerService.Result credit = ledger.credit(
                deposit.getWallet().getId(),
                deposit.getAmountVnd(),
                WalletEntryType.DEPOSIT,
                "deposit:" + deposit.getId(),
                "Wallet deposit"
        );
        deposit.markPaid(verified.providerTransactionId(), Instant.now());
        event.markProcessed();
        return new Result(
                event.getId(),
                deposit.getId(),
                event.getResult(),
                credit.balanceAfterVnd(),
                true
        );
    }

    private Result replayExisting(VerifiedNotification notification, byte[] requestHash) {
        WebhookEvent sameEvent = events.findByProviderCodeAndProviderEventId(
                notification.providerCode(), notification.providerEventId()
        ).orElse(null);
        if (sameEvent != null) {
            if (!Arrays.equals(sameEvent.getRequestHash(), requestHash)
                    || !Objects.equals(sameEvent.getProviderTransactionId(), notification.providerTransactionId())) {
                throw new WebhookIdentityConflictException();
            }
            return replayAgainstReference(sameEvent, notification, false);
        }

        WebhookEvent sameTransaction = events.findByProviderCodeAndProviderTransactionId(
                        notification.providerCode(), notification.providerTransactionId()
                )
                .orElseThrow(() -> new IllegalStateException("Conflicting webhook event could not be loaded"));
        return replayAgainstReference(sameTransaction, notification, true);
    }

    private Result replayAgainstReference(
            WebhookEvent event, VerifiedNotification notification, boolean requireKnownDeposit
    ) {
        Deposit referencedDeposit = deposits.lockByProviderAndReference(
                        notification.providerCode(), notification.merchantReference()
                )
                .orElse(null);
        Deposit existingDeposit = event.getDeposit();

        if (requireKnownDeposit && (existingDeposit == null || referencedDeposit == null)) {
            throw new WebhookIdentityConflictException();
        }
        if (existingDeposit != null && (referencedDeposit == null
                || !existingDeposit.getId().equals(referencedDeposit.getId()))) {
            throw new WebhookIdentityConflictException();
        }
        return result(event, existingDeposit, false);
    }

    private Result result(WebhookEvent event, Deposit deposit, boolean created) {
        return new Result(
                event.getId(),
                deposit == null ? null : deposit.getId(),
                event.getResult(),
                deposit == null ? null : deposit.getWallet().getBalance().longValueExact(),
                created
        );
    }

    private boolean paymentTermsMatch(Deposit deposit, VerifiedNotification notification) {
        return deposit.getAmountVnd() == notification.amountVnd()
                && deposit.getCurrency().equals(notification.currency());
    }

    private byte[] requireDigest(byte[] value) {
        byte[] copy = Arrays.copyOf(Objects.requireNonNull(value, "requestHash is required"), value.length);
        if (copy.length != 32) {
            throw new IllegalArgumentException("requestHash must be a SHA-256 digest");
        }
        return copy;
    }

    public record Result(
            UUID eventId,
            UUID depositId,
            WebhookEventResult result,
            Long walletBalanceVnd,
            boolean created
    ) {
    }
}
