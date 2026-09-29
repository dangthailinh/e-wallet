package com.github.yildizmy.payment;

public class WebhookIdentityConflictException extends RuntimeException {

    public WebhookIdentityConflictException() {
        super("Webhook identity conflicts with a previous delivery");
    }
}
