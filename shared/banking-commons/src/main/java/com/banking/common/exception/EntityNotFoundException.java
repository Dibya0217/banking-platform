package com.banking.common.exception;

public class EntityNotFoundException extends BankingException {

    public EntityNotFoundException(String entityType, Object id) {
        super("NOT_FOUND", entityType + " not found with id: " + id);
    }

    public EntityNotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
