package com.github.yildizmy.payment.momo;

public final class MomoProviderException extends RuntimeException {

    MomoProviderException() {
        super("MoMo create-payment request failed");
    }

    MomoProviderException(String message) {
        super(message);
    }

}
