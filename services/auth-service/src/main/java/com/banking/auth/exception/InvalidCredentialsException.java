package com.banking.auth.exception;

import com.banking.common.exception.BankingException;

public class InvalidCredentialsException extends BankingException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid email or password.");
    }
}
