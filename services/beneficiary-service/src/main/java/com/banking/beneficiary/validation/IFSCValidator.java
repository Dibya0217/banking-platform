package com.banking.beneficiary.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class IFSCValidator implements ConstraintValidator<ValidIFSC, String> {

    private static final Pattern IFSC_PATTERN = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return false;
        return IFSC_PATTERN.matcher(value).matches();
    }
}
