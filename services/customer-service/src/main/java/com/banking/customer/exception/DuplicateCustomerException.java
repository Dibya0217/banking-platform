package com.banking.customer.exception;

import com.banking.common.exception.BusinessRuleException;

public class DuplicateCustomerException extends BusinessRuleException {

    public DuplicateCustomerException(String message) {
        super("DUPLICATE_CUSTOMER", message);
    }
}
