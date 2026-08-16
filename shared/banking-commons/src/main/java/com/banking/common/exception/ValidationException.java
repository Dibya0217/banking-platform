package com.banking.common.exception;

public class ValidationException extends BankingException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

    public ValidationException(String field, String message) {
        super("VALIDATION_ERROR", "Validation failed for field '" + field + "': " + message);
    }
}
