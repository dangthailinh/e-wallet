package com.github.yildizmy.payment;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.net.URI;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentInstructions(URI redirectUrl, String qrPayload) {

    private static final int MAX_QR_PAYLOAD_LENGTH = 4_096;

    public PaymentInstructions {
        if ((redirectUrl == null) == (qrPayload == null)) {
            throw new IllegalArgumentException("exactly one payment instruction must be provided");
        }
        if (redirectUrl != null && (!"https".equalsIgnoreCase(redirectUrl.getScheme())
                || redirectUrl.getHost() == null || redirectUrl.getUserInfo() != null)) {
            throw new IllegalArgumentException("redirectUrl must be an absolute HTTPS URL without user info");
        }
        if (qrPayload != null && (qrPayload.isBlank()
                || qrPayload.length() > MAX_QR_PAYLOAD_LENGTH
                || !qrPayload.equals(qrPayload.trim()))) {
            throw new IllegalArgumentException("qrPayload must contain trimmed payment data");
        }
    }

    public static PaymentInstructions redirect(URI redirectUrl) {
        return new PaymentInstructions(redirectUrl, null);
    }

    public static PaymentInstructions qr(String qrPayload) {
        return new PaymentInstructions(null, qrPayload);
    }
}
