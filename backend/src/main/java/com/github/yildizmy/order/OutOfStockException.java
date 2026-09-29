package com.github.yildizmy.order;

public class OutOfStockException extends RuntimeException {

    public OutOfStockException() {
        super("Product is out of stock");
    }
}
