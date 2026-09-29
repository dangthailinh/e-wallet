package com.github.yildizmy.payment.momo;

import com.github.yildizmy.config.BusinessMetrics;
import com.github.yildizmy.payment.VerifiedNotification;
import com.github.yildizmy.payment.WebhookProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnProperty(prefix = "app.payment.momo", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/webhooks/momo")
public class MomoWebhookController {

    private final MomoPaymentProvider provider;
    private final WebhookProcessor processor;
    private final ObjectProvider<BusinessMetrics> metrics;

    @Autowired
    public MomoWebhookController(
            MomoPaymentProvider provider,
            WebhookProcessor processor,
            ObjectProvider<BusinessMetrics> metrics
    ) {
        this.provider = provider;
        this.processor = processor;
        this.metrics = metrics;
    }

    /** Convenience overload retained for standalone controller tests. */
    public MomoWebhookController(
            MomoPaymentProvider provider,
            WebhookProcessor processor,
            BusinessMetrics metrics
    ) {
        this(provider, processor, new StaticMetricsProvider(metrics));
    }

    private static final class StaticMetricsProvider implements ObjectProvider<BusinessMetrics> {
        private final BusinessMetrics value;

        private StaticMetricsProvider(BusinessMetrics value) {
            this.value = value;
        }

        @Override
        public BusinessMetrics getObject(Object... args) {
            return value;
        }

        @Override
        public BusinessMetrics getIfAvailable() {
            return value;
        }

        @Override
        public BusinessMetrics getIfUnique() {
            return value;
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader HttpHeaders headers
    ) {
        Map<String, List<String>> headerValues = headers.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        VerifiedNotification notification;
        try {
            notification = provider.verify(rawBody, headerValues);
        } catch (MomoNotificationPendingException pending) {
            metrics.ifAvailable(BusinessMetrics::webhookPending);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (MomoNotificationInvalidException invalid) {
            metrics.ifAvailable(BusinessMetrics::webhookInvalid);
            throw invalid;
        }
        processor.process(notification, sha256(rawBody));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
