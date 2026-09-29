package com.github.yildizmy.payment.momo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.yildizmy.payment.PaymentInstructions;
import com.github.yildizmy.payment.PaymentPreparation;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class MomoClient {

    private static final long MINIMUM_AMOUNT_VND = 1_000;
    private static final long MAXIMUM_AMOUNT_VND = 50_000_000;
    private static final String REQUEST_TYPE = "captureWallet";

    private final RestClient restClient;
    private final MomoProperties properties;

    public MomoClient(RestClient restClient, MomoProperties properties) {
        this.restClient = Objects.requireNonNull(restClient, "restClient is required");
        this.properties = Objects.requireNonNull(properties, "properties are required");
    }

    public PaymentInstructions create(PaymentPreparation payment) {
        Objects.requireNonNull(payment, "payment is required");
        if (payment.amountVnd() < MINIMUM_AMOUNT_VND || payment.amountVnd() > MAXIMUM_AMOUNT_VND) {
            throw new IllegalArgumentException("MoMo amount must be between 1,000 and 50,000,000 VND");
        }

        String requestId = payment.depositId().toString().replace("-", "");
        MomoCreateRequest unsigned = new MomoCreateRequest(
                properties.partnerCode(),
                REQUEST_TYPE,
                properties.ipnUrl().toString(),
                properties.redirectUrl().toString(),
                payment.merchantReference(),
                payment.amountVnd(),
                "Nap tien vi " + payment.merchantReference(),
                requestId,
                "",
                "vi",
                null
        );
        MomoCreateRequest request = unsigned.withSignature(
                MomoSignature.signCreate(properties.accessKey(), properties.secretKey(), unsigned)
        );

        MomoCreateResponse response;
        try {
            response = restClient.post()
                    .uri(properties.createUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MomoCreateResponse.class);
        } catch (RestClientException exception) {
            throw new MomoProviderException();
        }

        if (!validResponse(response, request)
                || response.signature() != null && !MomoSignature.verifiesCreateResponse(
                        properties.accessKey(), properties.secretKey(), response
                )) {
            throw new MomoProviderException();
        }
        return PaymentInstructions.redirect(validatedPayUrl(response.payUrl()));
    }

    public Optional<MomoQueryResponse> query(PaymentPreparation payment) {
        Objects.requireNonNull(payment, "payment is required");
        String requestId = payment.depositId().toString().replace("-", "");
        MomoQueryRequest unsigned = new MomoQueryRequest(
                properties.partnerCode(),
                payment.merchantReference(),
                requestId,
                "queryTransaction",
                null
        );
        MomoQueryRequest request = unsigned.withSignature(
                MomoSignature.signQuery(properties.accessKey(), properties.secretKey(), unsigned)
        );
        MomoQueryResponse response;
        try {
            response = restClient.post()
                    .uri(properties.queryUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MomoQueryResponse.class);
        } catch (RestClientException exception) {
            throw new MomoProviderException("MoMo query request failed");
        }
        if (!validQueryResponse(response, request)
                || response.signature() != null && !MomoSignature.verifiesQueryResponse(
                properties.accessKey(), properties.secretKey(), response
        )) {
            throw new MomoProviderException("MoMo query response was invalid");
        }
        return Optional.of(response);
    }

    private boolean validResponse(MomoCreateResponse response, MomoCreateRequest request) {
        return response != null
                && response.resultCode() == 0
                && properties.partnerCode().equals(response.partnerCode())
                && request.orderId().equals(response.orderId())
                && request.requestId().equals(response.requestId())
                && request.amount() == response.amount()
                && response.responseTime() > 0
                && response.message() != null
                && response.payUrl() != null;
    }

    private boolean validQueryResponse(MomoQueryResponse response, MomoQueryRequest request) {
        return response != null
                && properties.partnerCode().equals(response.partnerCode())
                && request.orderId().equals(response.orderId())
                && request.requestId().equals(response.requestId())
                && response.amount() > 0
                && response.responseTime() > 0
                && response.message() != null
                && response.extraData() != null
                && response.transId() >= 0
                && response.resultCode() >= 0
                && response.signature() != null
                && response.signature().matches("[0-9a-f]{64}");
    }

    private URI validatedPayUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || !(host.equals("momo.vn") || host.endsWith(".momo.vn"))) {
                throw new MomoProviderException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new MomoProviderException();
        }
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record MomoCreateRequest(
        String partnerCode,
        String requestType,
        String ipnUrl,
        String redirectUrl,
        String orderId,
        long amount,
        String orderInfo,
        String requestId,
        String extraData,
        String lang,
        String signature
) {
    MomoCreateRequest withSignature(String value) {
        return new MomoCreateRequest(
                partnerCode, requestType, ipnUrl, redirectUrl, orderId, amount,
                orderInfo, requestId, extraData, lang, value
        );
    }
}

record MomoCreateResponse(
        String partnerCode,
        String orderId,
        String requestId,
        long amount,
        long responseTime,
        String message,
        int resultCode,
        String payUrl,
        String signature
) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record MomoQueryRequest(
        String partnerCode,
        String orderId,
        String requestId,
        String requestType,
        String signature
) {
    MomoQueryRequest withSignature(String value) {
        return new MomoQueryRequest(partnerCode, orderId, requestId, requestType, value);
    }
}

record MomoQueryResponse(
        String partnerCode,
        String orderId,
        String requestId,
        long amount,
        long responseTime,
        String message,
        int resultCode,
        long transId,
        String extraData,
        String signature
) {
}
