package com.github.yildizmy.payment;

import com.github.yildizmy.domain.entity.User;
import com.github.yildizmy.domain.entity.Wallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
@Entity
@Table(name = "payment_deposits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deposit {

    private static final Pattern PROVIDER_CODE = Pattern.compile("[A-Z0-9_]{2,40}");
    private static final Pattern MERCHANT_REFERENCE = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 8)
    private DepositChannel channel;

    @Column(name = "provider_code", nullable = false, updatable = false, length = 40)
    private String providerCode;

    @Column(name = "merchant_reference", nullable = false, updatable = false, unique = true, length = 100)
    private String merchantReference;

    @Column(name = "provider_transaction_id", length = 160)
    private String providerTransactionId;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DepositStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "needs_reconciliation_review", nullable = false)
    private boolean needsReconciliationReview;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Deposit(
            User user, Wallet wallet, DepositChannel channel, String providerCode,
            String merchantReference, long amountVnd, Instant expiresAt
    ) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user, "user is required");
        this.wallet = Objects.requireNonNull(wallet, "wallet is required");
        this.channel = Objects.requireNonNull(channel, "channel is required");
        this.providerCode = requirePattern(providerCode, PROVIDER_CODE, "providerCode");
        if (channel == DepositChannel.MOMO && !"MOMO".equals(providerCode)
                || channel == DepositChannel.BANK && "MOMO".equals(providerCode)) {
            throw new IllegalArgumentException("channel and providerCode do not match");
        }
        this.merchantReference = requirePattern(merchantReference, MERCHANT_REFERENCE, "merchantReference");
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        this.amountVnd = amountVnd;
        this.currency = "VND";
        this.status = DepositStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after creation");
        }
    }

    @PreUpdate
    private void touchUpdatedAt() {
        Instant observedAt = Instant.now();
        if (createdAt != null && createdAt.isAfter(observedAt)) {
            observedAt = createdAt;
        }
        if (updatedAt != null && updatedAt.isAfter(observedAt)) {
            observedAt = updatedAt;
        }
        updatedAt = observedAt;
    }

    public void markPaid(String transactionId, Instant settlementTime) {
        requireStatus(DepositStatus.PENDING, DepositStatus.REVIEW);
        providerTransactionId = requireText(transactionId, "transactionId", 160);
        paidAt = Objects.requireNonNull(settlementTime, "settlementTime is required");
        if (paidAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("settlementTime must not precede creation");
        }
        status = DepositStatus.PAID;
    }

    public void markFailed() {
        requireStatus(DepositStatus.PENDING, DepositStatus.REVIEW);
        status = DepositStatus.FAILED;
    }

    public void markForReview() {
        requireStatus(DepositStatus.PENDING);
        status = DepositStatus.REVIEW;
    }

    public void flagForReconciliationReview() {
        if (status != DepositStatus.PAID && status != DepositStatus.FAILED
                && status != DepositStatus.EXPIRED) {
            throw new IllegalStateException("only terminal deposits can be flagged for reconciliation review");
        }
        needsReconciliationReview = true;
    }

    private static String requirePattern(String value, Pattern pattern, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName + " is required");
        if (!pattern.matcher(text).matches()) {
            throw new IllegalArgumentException(fieldName + " has invalid format");
        }
        return text;
    }

    private static String requireText(String value, String fieldName, int maximumLength) {
        String text = Objects.requireNonNull(value, fieldName + " is required");
        if (text.isEmpty() || text.length() > maximumLength || !text.equals(text.trim())) {
            throw new IllegalArgumentException(fieldName + " must contain trimmed text");
        }
        return text;
    }

    private void requireStatus(DepositStatus... allowed) {
        if (Arrays.stream(allowed).noneMatch(candidate -> candidate == status)) {
            throw new IllegalStateException("Deposit status does not allow this transition");
        }
    }
}
