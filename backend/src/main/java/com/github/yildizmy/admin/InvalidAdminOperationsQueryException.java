package com.github.yildizmy.admin;

public class InvalidAdminOperationsQueryException extends RuntimeException {

    public InvalidAdminOperationsQueryException() {
        super("Admin operation query parameters are invalid.");
    }
}
