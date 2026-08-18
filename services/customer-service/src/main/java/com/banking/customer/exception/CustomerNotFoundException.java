package com.banking.customer.exception;

import com.banking.common.exception.EntityNotFoundException;

import java.util.UUID;

public class CustomerNotFoundException extends EntityNotFoundException {

    public CustomerNotFoundException(UUID id) {
        super("Customer", id);
    }
}
