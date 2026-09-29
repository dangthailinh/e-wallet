package com.github.yildizmy.payment.momo;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

final class MomoSignature {

    private static final String HMAC_SHA_256 = "HmacSHA256";

    private MomoSignature() {
    }

    static String canonicalCreateRequest(String accessKey, MomoCreateRequest request) {
        return "accessKey=" + accessKey
                + "&amount=" + request.amount()
                + "&extraData=" + request.extraData()
                + "&ipnUrl=" + request.ipnUrl()
                + "&orderId=" + request.orderId()
                + "&orderInfo=" + request.orderInfo()
                + "&partnerCode=" + request.partnerCode()
                + "&redirectUrl=" + request.redirectUrl()
                + "&requestId=" + request.requestId()
                + "&requestType=" + request.requestType();
    }

    static String signCreate(String accessKey, String secretKey, MomoCreateRequest request) {
        return hmac(secretKey, canonicalCreateRequest(accessKey, request));
    }

    static String signQuery(String accessKey, String secretKey, MomoQueryRequest request) {
        return hmac(secretKey, canonicalQuery(accessKey, request));
    }

    static boolean verifiesQueryResponse(String accessKey, String secretKey, MomoQueryResponse response) {
        if (response.signature() == null) return false;
        String expected = hmac(secretKey, canonicalQueryResponse(accessKey, response));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                response.signature().getBytes(StandardCharsets.US_ASCII)
        );
    }

    static String signQueryResponse(String accessKey, String secretKey, MomoQueryResponse response) {
        return hmac(secretKey, canonicalQueryResponse(accessKey, response));
    }

    static String signCreateResponse(String accessKey, String secretKey, MomoCreateResponse response) {
        return hmac(secretKey, canonicalCreateResponse(accessKey, response));
    }

    static boolean verifiesCreateResponse(String accessKey, String secretKey, MomoCreateResponse response) {
        if (response.signature() == null) return false;
        byte[] expected = signCreateResponse(accessKey, secretKey, response).getBytes(StandardCharsets.US_ASCII);
        byte[] supplied = response.signature().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, supplied);
    }

    static boolean verifiesNotification(String accessKey, String secretKey, MomoNotification notification) {
        if (notification.signature() == null) return false;
        String expectedHex = hmac(secretKey, canonicalNotification(accessKey, notification));
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.US_ASCII),
                notification.signature().getBytes(StandardCharsets.US_ASCII)
        );
    }

    static String signNotification(String accessKey, String secretKey, MomoNotification notification) {
        return hmac(secretKey, canonicalNotification(accessKey, notification));
    }

    private static String canonicalCreateResponse(String accessKey, MomoCreateResponse response) {
        return "accessKey=" + accessKey
                + "&amount=" + response.amount()
                + "&message=" + response.message()
                + "&orderId=" + response.orderId()
                + "&partnerCode=" + response.partnerCode()
                + "&payUrl=" + response.payUrl()
                + "&requestId=" + response.requestId()
                + "&responseTime=" + response.responseTime()
                + "&resultCode=" + response.resultCode();
    }

    private static String canonicalQuery(String accessKey, MomoQueryRequest request) {
        return "accessKey=" + accessKey
                + "&orderId=" + request.orderId()
                + "&partnerCode=" + request.partnerCode()
                + "&requestId=" + request.requestId();
    }

    private static String canonicalQueryResponse(String accessKey, MomoQueryResponse response) {
        return "accessKey=" + accessKey
                + "&amount=" + response.amount()
                + "&extraData=" + response.extraData()
                + "&message=" + response.message()
                + "&orderId=" + response.orderId()
                + "&partnerCode=" + response.partnerCode()
                + "&requestId=" + response.requestId()
                + "&responseTime=" + response.responseTime()
                + "&resultCode=" + response.resultCode()
                + "&transId=" + response.transId();
    }

    private static String canonicalNotification(String accessKey, MomoNotification value) {
        return "accessKey=" + accessKey
                + "&amount=" + value.amount()
                + "&extraData=" + value.extraData()
                + "&message=" + value.message()
                + "&orderId=" + value.orderId()
                + "&orderInfo=" + value.orderInfo()
                + "&orderType=" + value.orderType()
                + "&partnerCode=" + value.partnerCode()
                + "&payType=" + value.payType()
                + "&requestId=" + value.requestId()
                + "&responseTime=" + value.responseTime()
                + "&resultCode=" + value.resultCode()
                + "&transId=" + value.transId();
    }

    private static String hmac(String secretKey, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
