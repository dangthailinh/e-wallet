package com.github.yildizmy.payment;

import com.github.yildizmy.audit.AuditLog;
import com.github.yildizmy.audit.AuditLogRepository;
import com.github.yildizmy.admin.AdminPageResponse;
import com.github.yildizmy.admin.InvalidAdminOperationsQueryException;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import com.github.yildizmy.wallet.WalletEntry;
import com.github.yildizmy.wallet.WalletEntryDirection;
import com.github.yildizmy.wallet.WalletEntryRepository;
import com.github.yildizmy.wallet.WalletEntryType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private static final Duration QUERY_WINDOW = Duration.ofMinutes(1);
    private static final int MAX_QUERIES_PER_WINDOW = 3;

    private final DepositRepository deposits;
    private final WalletEntryRepository entries;
    private final AuditLogRepository audits;
    private final List<PaymentProvider> configuredProviders;
    private final Map<QueryKey, Deque<Instant>> queryAttempts = new ConcurrentHashMap<>();

    @Transactional
    public ReconciliationResult reconcile(long actorUserId, UUID depositId, String requestId) {
        requireActor(actorUserId);
        Deposit deposit = requireDeposit(depositId);
        ReconciliationResult result = inspect(deposit, true);
        audits.saveAndFlush(AuditLog.record(
                actorUserId,
                "DEPOSIT_RECONCILIATION_CHECKED",
                "DEPOSIT",
                deposit.getId(),
                null,
                requestId
        ));
        return result;
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<ReconciliationResult> list(int page, int size) {
        if (page < 0 || page > 10_000 || size < 1 || size > 100) {
            throw new InvalidAdminOperationsQueryException();
        }
        return AdminPageResponse.from(deposits.findForAdmin(
                        null,
                        null,
                        null,
                        PageRequest.of(page, size, Sort.by(
                                Sort.Order.desc("createdAt"), Sort.Order.desc("id")
                        ))
                ).map(deposit -> inspect(deposit, false)));
    }

    /**
     * Re-query is deliberately evidence-only. Even a successful provider response
     * never calls the wallet ledger; money movement remains webhook/inbox driven
     * and a mismatch is left for an explicitly reviewed operation.
     */
    @Transactional
    public ProviderRequeryResult requery(long actorUserId, UUID depositId, String requestId) {
        requireActor(actorUserId);
        Deposit deposit = requireDeposit(depositId);
        checkRateLimit(actorUserId, depositId);
        audits.saveAndFlush(AuditLog.record(
                actorUserId,
                "DEPOSIT_REQUERY_REQUESTED",
                "DEPOSIT",
                deposit.getId(),
                null,
                requestId
        ));

        PaymentProvider provider = providerFor(deposit.getProviderCode()).orElse(null);
        if (provider == null) {
            return finishRequery(actorUserId, deposit, requestId, false, null,
                    false, "PROVIDER_REQUERY_UNSUPPORTED");
        }

        Optional<VerifiedNotification> query;
        try {
            query = provider.requery(new PaymentPreparation(
                    deposit.getId(),
                    deposit.getMerchantReference(),
                    deposit.getAmountVnd(),
                    deposit.getCurrency(),
                    deposit.getExpiresAt()
            ));
        } catch (RuntimeException providerFailure) {
            // Keep the operator action auditable even when the upstream is down.
            // The sanitized reason intentionally omits provider response bodies.
            return finishRequery(actorUserId, deposit, requestId, true, null,
                    true, "PROVIDER_QUERY_FAILED");
        }
        if (query.isEmpty()) {
            return finishRequery(actorUserId, deposit, requestId, false, null,
                    false, "PROVIDER_REQUERY_UNSUPPORTED");
        }

        VerifiedNotification notification = query.get();
        boolean termsMatch = provider.providerCode().equals(notification.providerCode())
                && deposit.getAmountVnd() == notification.amountVnd()
                && deposit.getCurrency().equals(notification.currency())
                && deposit.getMerchantReference().equals(notification.merchantReference());
        ReconciliationResult ledgerEvidence = inspect(deposit, true);
        boolean mismatch = !termsMatch
                || notification.outcome() == VerifiedNotification.Outcome.SUCCEEDED
                && (deposit.getStatus() != DepositStatus.PAID || ledgerEvidence.mismatch())
                || notification.outcome() == VerifiedNotification.Outcome.FAILED
                && deposit.getStatus() == DepositStatus.PAID;
        String reason = !termsMatch
                ? "PAYMENT_TERMS_MISMATCH"
                : mismatch ? "PROVIDER_STATUS_MISMATCH" : "MATCHED";
        if (mismatch) {
            flagTerminalDeposit(deposit);
        }
        return finishRequery(
                actorUserId,
                deposit,
                requestId,
                true,
                notification.outcome().name(),
                mismatch,
                reason
        );
    }

    private ProviderRequeryResult finishRequery(
            long actorUserId,
            Deposit deposit,
            String requestId,
            boolean queried,
            String outcome,
            boolean mismatch,
            String reason
    ) {
        audits.saveAndFlush(AuditLog.record(
                actorUserId,
                mismatch ? "DEPOSIT_REQUERY_MISMATCH" : "DEPOSIT_REQUERY_COMPLETED",
                "DEPOSIT",
                deposit.getId(),
                null,
                requestId
        ));
        return new ProviderRequeryResult(
                deposit.getId(),
                queried,
                outcome,
                mismatch,
                deposit.isNeedsReconciliationReview(),
                reason,
                Instant.now()
        );
    }

    private ReconciliationResult inspect(Deposit deposit, boolean markTerminalReview) {
        String reference = "deposit:" + deposit.getId();
        Optional<WalletEntry> candidate = entries.findByBusinessReference(reference);
        WalletEntry entry = candidate.orElse(null);
        boolean present = entry != null;
        Long ledgerAmount = present ? entry.getAmountVnd() : null;
        String reason = "MATCHED";
        boolean mismatch = false;
        if (deposit.getStatus() == DepositStatus.PAID) {
            if (!present) {
                mismatch = true;
                reason = "LEDGER_ENTRY_MISSING";
            } else if (entry.getAmountVnd() != deposit.getAmountVnd()
                    || entry.getDirection() != WalletEntryDirection.CREDIT
                    || entry.getType() != WalletEntryType.DEPOSIT
                    || entry.getWallet() == null
                    || !Objects.equals(entry.getWallet().getId(), deposit.getWallet().getId())) {
                mismatch = true;
                reason = "LEDGER_ENTRY_MISMATCH";
            }
        } else if (present) {
            mismatch = true;
            reason = "LEDGER_ENTRY_FOR_UNPAID_DEPOSIT";
        }
        if (mismatch && markTerminalReview) {
            flagTerminalDeposit(deposit);
        }
        return new ReconciliationResult(
                deposit.getId(),
                deposit.getStatus(),
                deposit.getAmountVnd(),
                ledgerAmount,
                present,
                mismatch,
                deposit.isNeedsReconciliationReview(),
                reason,
                Instant.now()
        );
    }

    private void flagTerminalDeposit(Deposit deposit) {
        if (!deposit.isNeedsReconciliationReview()
                && (deposit.getStatus() == DepositStatus.PAID
                || deposit.getStatus() == DepositStatus.FAILED
                || deposit.getStatus() == DepositStatus.EXPIRED)) {
            deposit.flagForReconciliationReview();
        }
    }

    private Optional<PaymentProvider> providerFor(String providerCode) {
        return configuredProviders.stream()
                .filter(provider -> providerCode.equals(provider.providerCode()))
                .findFirst();
    }

    private Deposit requireDeposit(UUID depositId) {
        return deposits.findById(Objects.requireNonNull(depositId, "depositId is required"))
                .orElseThrow(() -> new NoSuchElementFoundException("Deposit not found"));
    }

    private void checkRateLimit(long actorUserId, UUID depositId) {
        QueryKey key = new QueryKey(actorUserId, depositId);
        Instant now = Instant.now();
        Deque<Instant> attempts = queryAttempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            Instant cutoff = now.minus(QUERY_WINDOW);
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
                attempts.removeFirst();
            }
            if (attempts.size() >= MAX_QUERIES_PER_WINDOW) {
                long retryAfter = Math.max(1, Duration.between(now, attempts.peekFirst().plus(QUERY_WINDOW)).toSeconds());
                throw new ReconciliationRateLimitException(retryAfter);
            }
            attempts.addLast(now);
        }
    }

    private static void requireActor(long actorUserId) {
        if (actorUserId <= 0) {
            throw new IllegalArgumentException("actorUserId must be positive");
        }
    }

    private record QueryKey(long actorUserId, UUID depositId) {
    }
}
