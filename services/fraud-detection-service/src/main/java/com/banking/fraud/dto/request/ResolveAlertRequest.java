package com.banking.fraud.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveAlertRequest {

    @NotBlank
    private String note;
}
