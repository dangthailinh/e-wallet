package com.github.yildizmy.exception;

public class InvalidCatalogQueryException extends RuntimeException {

    public InvalidCatalogQueryException() {
        super("Catalog query parameters are invalid.");
    }
}
