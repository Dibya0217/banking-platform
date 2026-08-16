package com.banking.common.exception;

import lombok.Getter;

@Getter
public class BankingException extends RuntimeException {

    private final String errorCode;

    public BankingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BankingException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
