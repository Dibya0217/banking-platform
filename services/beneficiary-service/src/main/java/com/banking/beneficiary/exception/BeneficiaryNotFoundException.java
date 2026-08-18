package com.banking.beneficiary.exception;

import com.banking.common.exception.EntityNotFoundException;

import java.util.UUID;

public class BeneficiaryNotFoundException extends EntityNotFoundException {

    public BeneficiaryNotFoundException(UUID id) {
        super("Beneficiary", id);
    }
}
