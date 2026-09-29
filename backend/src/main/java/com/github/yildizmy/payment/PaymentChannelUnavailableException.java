package com.github.yildizmy.payment;

public class PaymentChannelUnavailableException extends RuntimeException {

    public PaymentChannelUnavailableException() {
        super("The selected payment channel is not configured");
    }
}
