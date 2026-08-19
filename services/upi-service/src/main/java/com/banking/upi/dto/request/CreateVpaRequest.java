package com.banking.upi.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateVpaRequest {

    @NotNull
    private UUID accountId;

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9._-]+@[a-zA-Z0-9]+$",
             message = "VPA must be in format handle@bank (e.g. priya@bank)")
    private String vpa;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "PIN must be exactly 6 digits")
    private String pin;

    @DecimalMin(value = "100.00")
    @DecimalMax(value = "200000.00")
    private BigDecimal dailyLimit;
}
