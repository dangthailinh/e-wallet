package com.github.yildizmy.payment;

public class ReconciliationRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public ReconciliationRateLimitException(long retryAfterSeconds) {
        super("Provider re-query rate limit exceeded");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
