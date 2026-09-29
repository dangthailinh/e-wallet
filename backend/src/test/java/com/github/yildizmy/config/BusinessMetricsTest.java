package com.github.yildizmy.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessMetricsTest {

    @Test
    void recordsBoundedBusinessOutcomesAndProviderLatency() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BusinessMetrics metrics = new BusinessMetrics(registry);

        metrics.webhookInvalid();
        metrics.webhookDuplicate();
        metrics.webhookPending();
        metrics.checkout(true);
        metrics.checkout(false);
        metrics.checkoutFailed();
        metrics.setLowStockProducts(3);
        assertThat(metrics.timeDepositProvider("MOMO", () -> "prepared")).isEqualTo("prepared");

        assertThat(registry.counter("ewallet.webhook.events", "result", "invalid").count()).isEqualTo(1);
        assertThat(registry.counter("ewallet.webhook.events", "result", "duplicate").count()).isEqualTo(1);
        assertThat(registry.counter("ewallet.webhook.events", "result", "pending").count()).isEqualTo(1);
        assertThat(registry.counter("ewallet.checkout.results", "result", "created").count()).isEqualTo(1);
        assertThat(registry.counter("ewallet.checkout.results", "result", "idempotent").count()).isEqualTo(1);
        assertThat(registry.counter("ewallet.checkout.results", "result", "failed").count()).isEqualTo(1);
        assertThat(registry.get("ewallet.inventory.low_stock_products").gauge().value()).isEqualTo(3);
        assertThat(registry.get("ewallet.deposit.provider_latency")
                .tag("provider", "MOMO").timer().count()).isEqualTo(1);
    }
}
