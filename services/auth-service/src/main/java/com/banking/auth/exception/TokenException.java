package com.banking.auth.exception;

import com.banking.common.exception.BankingException;

public class TokenException extends BankingException {

    public TokenException(String message) {
        super("TOKEN_INVALID", message);
    }
}
