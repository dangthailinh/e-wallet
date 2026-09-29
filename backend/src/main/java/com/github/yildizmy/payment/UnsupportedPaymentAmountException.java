package com.github.yildizmy.payment;

public final class UnsupportedPaymentAmountException extends RuntimeException {

    public UnsupportedPaymentAmountException() {
        super("The amount is outside the selected payment channel limits");
    }
}
