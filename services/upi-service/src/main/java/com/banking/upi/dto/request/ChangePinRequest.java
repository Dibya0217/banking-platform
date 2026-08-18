package com.banking.upi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChangePinRequest {

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "Current PIN must be exactly 6 digits")
    private String currentPin;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "New PIN must be exactly 6 digits")
    private String newPin;
}
