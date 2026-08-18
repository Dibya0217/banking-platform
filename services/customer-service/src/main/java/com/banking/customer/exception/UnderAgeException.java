package com.banking.customer.exception;

import com.banking.common.exception.BusinessRuleException;

public class UnderAgeException extends BusinessRuleException {

    public UnderAgeException() {
        super("UNDER_AGE", "Customer must be at least 18 years old to register");
    }
}
