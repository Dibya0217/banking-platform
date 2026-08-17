package com.banking.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateCredentialRequest(
        @NotNull UUID customerId,
        @NotBlank @Email String email,
        @NotBlank String mobile,
        @NotBlank String password
) {}
