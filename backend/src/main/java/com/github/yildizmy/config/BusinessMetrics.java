package com.github.yildizmy.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Bounded business metrics. Values used as tags are deliberately allow-listed so provider
 * payloads, request IDs and other untrusted values can never create an unbounded time series.
 */
@Component
public final class BusinessMetrics {

    private final Counter webhookInvalid;
    private final Counter webhookDuplicate;
    private final Counter webhookPending;
    private final Counter checkoutCreated;
    private final Counter checkoutIdempotent;
    private final Counter checkoutFailed;
    private final AtomicLong lowStockProducts = new AtomicLong();
    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry is required");
        webhookInvalid = Counter.builder("ewallet.webhook.events")
                .description("Rejected provider webhook notifications")
                .tag("result", "invalid")
                .register(registry);
        webhookDuplicate = Counter.builder("ewallet.webhook.events")
                .description("Duplicate provider webhook notifications")
                .tag("result", "duplicate")
                .register(registry);
        webhookPending = Counter.builder("ewallet.webhook.events")
                .description("Provider webhook notifications deferred for review")
                .tag("result", "pending")
                .register(registry);
        checkoutCreated = Counter.builder("ewallet.checkout.results")
                .description("Successful new wallet checkouts")
                .tag("result", "created")
                .register(registry);
        checkoutIdempotent = Counter.builder("ewallet.checkout.results")
                .description("Successful idempotent checkout replays")
                .tag("result", "idempotent")
                .register(registry);
        checkoutFailed = Counter.builder("ewallet.checkout.results")
                .description("Failed wallet checkout attempts")
                .tag("result", "failed")
                .register(registry);
        registry.gauge(
                "ewallet.inventory.low_stock_products",
                lowStockProducts,
                AtomicLong::doubleValue
        );
    }

    public void webhookInvalid() {
        webhookInvalid.increment();
    }

    public void webhookDuplicate() {
        webhookDuplicate.increment();
    }

    public void webhookPending() {
        webhookPending.increment();
    }

    public void checkout(boolean created) {
        (created ? checkoutCreated : checkoutIdempotent).increment();
    }

    public void checkoutFailed() {
        checkoutFailed.increment();
    }

    public void setLowStockProducts(long count) {
        lowStockProducts.set(Math.max(0, count));
    }

    public <T> T timeDepositProvider(String providerCode, Supplier<T> operation) {
        String provider = switch (Objects.requireNonNull(providerCode, "providerCode is required")) {
            case "MOMO" -> "MOMO";
            default -> "OTHER";
        };
        Timer.Sample sample = Timer.start(registry);
        Timer timer = Timer.builder("ewallet.deposit.provider_latency")
                .description("Time spent preparing a provider deposit payment")
                .publishPercentileHistogram()
                .tag("provider", provider)
                .register(registry);
        try {
            return operation.get();
        } finally {
            sample.stop(timer);
        }
    }
}
