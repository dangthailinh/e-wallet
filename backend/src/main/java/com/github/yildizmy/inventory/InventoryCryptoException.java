package com.github.yildizmy.inventory;

public class InventoryCryptoException extends RuntimeException {

    public InventoryCryptoException(String message) {
        super(message);
    }

    public InventoryCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
