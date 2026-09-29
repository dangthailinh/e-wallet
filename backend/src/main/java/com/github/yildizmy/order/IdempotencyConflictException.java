package com.github.yildizmy.order;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency key belongs to another checkout request");
    }
}
