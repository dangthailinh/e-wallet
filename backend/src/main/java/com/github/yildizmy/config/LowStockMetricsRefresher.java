package com.github.yildizmy.config;

import com.github.yildizmy.catalog.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Refreshes a bounded low-stock gauge without exposing product names or inventory contents. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LowStockMetricsRefresher {

    private final ProductRepository products;
    private final BusinessMetrics metrics;

    @Value("${app.observability.low-stock-threshold:1}")
    private int threshold;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${app.observability.low-stock-refresh:30000}")
    public void refresh() {
        if (threshold < 0) {
            log.warn("Invalid low-stock metric threshold; using zero");
            threshold = 0;
        }
        try {
            metrics.setLowStockProducts(products.countLowStockProducts(threshold));
        } catch (RuntimeException exception) {
            // Metrics must never make a customer request or application startup fail.
            log.warn("Unable to refresh low-stock metric ({})", exception.getClass().getSimpleName());
        }
    }
}
