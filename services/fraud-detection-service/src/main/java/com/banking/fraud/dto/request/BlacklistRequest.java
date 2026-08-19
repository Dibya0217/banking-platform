package com.banking.fraud.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class BlacklistRequest {

    @NotNull
    private UUID accountId;

    private String reason;
}
