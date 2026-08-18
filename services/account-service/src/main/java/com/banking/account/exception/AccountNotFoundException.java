package com.banking.account.exception;

import com.banking.common.exception.EntityNotFoundException;

import java.util.UUID;

public class AccountNotFoundException extends EntityNotFoundException {

    public AccountNotFoundException(UUID id) {
        super("Account", id);
    }

    public AccountNotFoundException(String accountNumber) {
        super("Account not found with account number: " + accountNumber);
    }
}
