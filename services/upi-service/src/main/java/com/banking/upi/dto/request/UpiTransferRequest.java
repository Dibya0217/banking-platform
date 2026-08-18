package com.banking.upi.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpiTransferRequest {

    @NotBlank
    private String payerVpa;

    @NotBlank
    private String payeeVpa;

    @NotNull
    @DecimalMin(value = "1.00", message = "Minimum transfer amount is ₹1")
    private BigDecimal amount;

    @Size(max = 255)
    private String remarks;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "PIN must be exactly 6 digits")
    private String pin;

    @NotBlank
    @Size(min = 1, max = 64)
    private String idempotencyKey;
}
