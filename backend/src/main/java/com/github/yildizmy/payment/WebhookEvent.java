package com.github.yildizmy.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
@Entity
@Table(name = "webhook_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEvent {

    private static final Pattern PROVIDER_CODE = Pattern.compile("[A-Z0-9_]{2,40}");

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_id")
    private Deposit deposit;

    @Column(name = "provider_code", nullable = false, length = 40)
    private String providerCode;

    @Column(name = "provider_event_id", nullable = false, length = 160)
    private String providerEventId;

    @Column(name = "provider_transaction_id", length = 160)
    private String providerTransactionId;

    @Getter(AccessLevel.NONE)
    @Column(name = "request_hash", nullable = false)
    private byte[] requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WebhookEventResult result;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public WebhookEvent(
            Deposit deposit, String providerCode, String providerEventId,
            String providerTransactionId, byte[] requestHash
    ) {
        this.id = UUID.randomUUID();
        this.deposit = deposit;
        this.providerCode = Objects.requireNonNull(providerCode, "providerCode is required");
        if (!PROVIDER_CODE.matcher(providerCode).matches()) {
            throw new IllegalArgumentException("providerCode has invalid format");
        }
        if (deposit != null && !deposit.getProviderCode().equals(providerCode)) {
            throw new IllegalArgumentException("deposit provider does not match event provider");
        }
        this.providerEventId = requireText(providerEventId, "providerEventId");
        this.providerTransactionId = providerTransactionId == null
                ? null : requireText(providerTransactionId, "providerTransactionId");
        byte[] hash = Objects.requireNonNull(requestHash, "requestHash is required");
        if (hash.length != 32) {
            throw new IllegalArgumentException("requestHash must be a SHA-256 digest");
        }
        this.requestHash = hash.clone();
        this.result = WebhookEventResult.RECEIVED;
        this.receivedAt = Instant.now();
    }

    public byte[] getRequestHash() {
        return requestHash.clone();
    }

    public void attachTo(Deposit targetDeposit) {
        Deposit candidate = Objects.requireNonNull(targetDeposit, "deposit is required");
        if (deposit != null && !deposit.getId().equals(candidate.getId())) {
            throw new IllegalStateException("Webhook event is already attached to another deposit");
        }
        if (!providerCode.equals(candidate.getProviderCode())) {
            throw new IllegalArgumentException("deposit provider does not match event provider");
        }
        deposit = candidate;
    }

    public void markProcessed() {
        finish(WebhookEventResult.PROCESSED, null);
    }

    public void markIgnored(String errorCode) {
        finish(WebhookEventResult.IGNORED, requireErrorCode(errorCode));
    }

    public void markForReview(String errorCode) {
        finish(WebhookEventResult.REVIEW, requireErrorCode(errorCode));
    }

    private void finish(WebhookEventResult nextResult, String nextErrorCode) {
        if (result != WebhookEventResult.RECEIVED && result != WebhookEventResult.REVIEW) {
            throw new IllegalStateException("Webhook event is already terminal");
        }
        result = Objects.requireNonNull(nextResult, "nextResult is required");
        errorCode = nextErrorCode;
        Instant observedAt = Instant.now();
        processedAt = receivedAt.isAfter(observedAt) ? receivedAt : observedAt;
    }

    private String requireErrorCode(String value) {
        String code = Objects.requireNonNull(value, "errorCode is required");
        if (!code.matches("[A-Z0-9_]{1,80}")) {
            throw new IllegalArgumentException("errorCode has invalid format");
        }
        return code;
    }

    private static String requireText(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName + " is required");
        if (text.isEmpty() || text.length() > 160 || !text.equals(text.trim())) {
            throw new IllegalArgumentException(fieldName + " must contain 1 to 160 trimmed characters");
        }
        return text;
    }
}
