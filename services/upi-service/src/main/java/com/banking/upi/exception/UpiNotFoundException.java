package com.banking.upi.exception;

import com.banking.common.exception.EntityNotFoundException;

import java.util.UUID;

public class UpiNotFoundException extends EntityNotFoundException {

    public UpiNotFoundException(UUID id) {
        super("UPI VPA", id);
    }

    public UpiNotFoundException(String vpa) {
        super("UPI VPA not found: " + vpa);
    }
}
