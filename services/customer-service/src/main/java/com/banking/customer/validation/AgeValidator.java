package com.banking.customer.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class AgeValidator implements ConstraintValidator<AdultAge, LocalDate> {

    private static final int MINIMUM_AGE = 18;

    @Override
    public boolean isValid(LocalDate dateOfBirth, ConstraintValidatorContext context) {
        if (dateOfBirth == null) {
            return false;
        }
        return dateOfBirth.plusYears(MINIMUM_AGE).isBefore(LocalDate.now())
                || dateOfBirth.plusYears(MINIMUM_AGE).isEqual(LocalDate.now());
    }
}
