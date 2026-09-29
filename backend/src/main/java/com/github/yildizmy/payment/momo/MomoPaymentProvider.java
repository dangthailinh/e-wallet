package com.github.yildizmy.payment.momo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yildizmy.payment.DepositChannel;
import com.github.yildizmy.payment.PaymentInstructions;
import com.github.yildizmy.payment.PaymentPreparation;
import com.github.yildizmy.payment.PaymentProvider;
import com.github.yildizmy.payment.VerifiedNotification;
import com.github.yildizmy.payment.UnsupportedPaymentAmountException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class MomoPaymentProvider implements PaymentProvider {

    private static final String PROVIDER_CODE = "MOMO";
    private static final long MINIMUM_AMOUNT_VND = 1_000;
    private static final long MAXIMUM_AMOUNT_VND = 50_000_000;
    private static final Set<Integer> FINAL_FAILURE_CODES = Set.of(
            98, 99, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1017, 1026, 2019, 4001, 4002, 4100
    );

    private final MomoClient client;
    private final MomoProperties properties;
    private final ObjectMapper objectMapper;

    public MomoPaymentProvider(MomoClient client, MomoProperties properties, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client, "client is required");
        this.properties = Objects.requireNonNull(properties, "properties are required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
    }

    @Override
    public DepositChannel channel() {
        return DepositChannel.MOMO;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public void validateAmount(long amountVnd, String currency) {
        if (!"VND".equals(currency) || amountVnd < MINIMUM_AMOUNT_VND || amountVnd > MAXIMUM_AMOUNT_VND) {
            throw new UnsupportedPaymentAmountException();
        }
    }

    @Override
    public PaymentInstructions prepare(PaymentPreparation payment) {
        return client.create(payment);
    }

    @Override
    public Optional<VerifiedNotification> requery(PaymentPreparation payment) {
        return client.query(payment).map(response -> new VerifiedNotification(
                PROVIDER_CODE,
                response.requestId(),
                response.transId() > 0 ? Long.toString(response.transId()) : "QUERY-" + response.requestId(),
                response.orderId(),
                response.amount(),
                "VND",
                response.resultCode() == 0
                        ? VerifiedNotification.Outcome.SUCCEEDED
                        : VerifiedNotification.Outcome.FAILED,
                Instant.ofEpochMilli(response.responseTime())
        ));
    }

    @Override
    public VerifiedNotification verify(byte[] rawBody, Map<String, List<String>> headers) {
        Objects.requireNonNull(rawBody, "rawBody is required");
        Objects.requireNonNull(headers, "headers are required");
        MomoNotification notification = parse(rawBody);
        if (!valid(notification)
                || !MomoSignature.verifiesNotification(
                        properties.accessKey(), properties.secretKey(), notification
                )) {
            throw new MomoNotificationInvalidException();
        }
        if (notification.resultCode() != 0 && !FINAL_FAILURE_CODES.contains(notification.resultCode())) {
            throw new MomoNotificationPendingException();
        }

        return new VerifiedNotification(
                PROVIDER_CODE,
                notification.requestId(),
                Long.toString(notification.transId()),
                notification.orderId(),
                notification.amount(),
                "VND",
                notification.resultCode() == 0
                        ? VerifiedNotification.Outcome.SUCCEEDED
                        : VerifiedNotification.Outcome.FAILED,
                Instant.ofEpochMilli(notification.responseTime())
        );
    }

    private MomoNotification parse(byte[] rawBody) {
        if (rawBody.length == 0 || rawBody.length > 16_384) {
            throw new MomoNotificationInvalidException();
        }
        try {
            return objectMapper.readValue(new String(rawBody, StandardCharsets.UTF_8), MomoNotification.class);
        } catch (JsonProcessingException exception) {
            throw new MomoNotificationInvalidException();
        }
    }

    private boolean valid(MomoNotification value) {
        return value != null
                && properties.partnerCode().equals(value.partnerCode())
                && text(value.orderId(), 200)
                && text(value.requestId(), 50)
                && value.amount() >= MINIMUM_AMOUNT_VND && value.amount() <= MAXIMUM_AMOUNT_VND
                && text(value.orderInfo(), 255)
                && "momo_wallet".equals(value.orderType())
                && value.transId() > 0
                && text(value.message(), 255)
                && text(value.payType(), 32)
                && value.responseTime() > 0
                && value.extraData() != null && value.extraData().length() <= 1_000
                && value.signature() != null && value.signature().matches("[0-9a-f]{64}");
    }

    private boolean text(String value, int maximum) {
        return value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= maximum;
    }
}
