package com.github.yildizmy.order;

public class InvalidOrderQueryException extends RuntimeException {

    public InvalidOrderQueryException() {
        super("page must be 0..1000 and size must be 1..50");
    }
}
