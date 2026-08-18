package com.banking.customer.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AgeValidator.class)
@Documented
public @interface AdultAge {

    String message() default "Customer must be at least 18 years old";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
