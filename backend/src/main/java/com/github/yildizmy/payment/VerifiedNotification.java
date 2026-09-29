package com.github.yildizmy.payment;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record VerifiedNotification(
        String providerCode,
        String providerEventId,
        String providerTransactionId,
        String merchantReference,
        long amountVnd,
        String currency,
        Outcome outcome,
        Instant occurredAt
) {

    private static final Pattern PROVIDER_CODE = Pattern.compile("[A-Z0-9_]{2,40}");
    private static final Pattern MERCHANT_REFERENCE = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    public VerifiedNotification {
        providerCode = requirePattern(providerCode, PROVIDER_CODE, "providerCode", 40);
        providerEventId = requireText(providerEventId, "providerEventId", 160);
        providerTransactionId = requireText(providerTransactionId, "providerTransactionId", 160);
        merchantReference = requirePattern(merchantReference, MERCHANT_REFERENCE, "merchantReference", 100);
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        if (!"VND".equals(currency)) {
            throw new IllegalArgumentException("currency must be VND");
        }
        outcome = Objects.requireNonNull(outcome, "outcome is required");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    private static String requirePattern(
            String value, Pattern pattern, String fieldName, int maximumLength
    ) {
        String text = requireText(value, fieldName, maximumLength);
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

    public enum Outcome {
        SUCCEEDED,
        FAILED
    }
}
