package com.banking.common.exception;

public class BusinessRuleException extends BankingException {

    public BusinessRuleException(String errorCode, String message) {
        super(errorCode, message);
    }
}
