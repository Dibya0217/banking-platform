package com.banking.beneficiary.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = IFSCValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIFSC {

    String message() default "Invalid IFSC code. Format: 4 uppercase letters, 0, 6 alphanumeric characters (e.g. HDFC0001234)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
