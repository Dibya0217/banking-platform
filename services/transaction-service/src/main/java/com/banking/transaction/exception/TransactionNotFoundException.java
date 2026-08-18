package com.banking.transaction.exception;

import com.banking.common.exception.EntityNotFoundException;

import java.util.UUID;

public class TransactionNotFoundException extends EntityNotFoundException {

    public TransactionNotFoundException(UUID id) {
        super("Transaction", id);
    }
}
