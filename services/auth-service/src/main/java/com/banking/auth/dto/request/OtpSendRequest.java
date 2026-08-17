package com.banking.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpSendRequest(
        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number") String mobile,
        @NotBlank String purpose
) {}
