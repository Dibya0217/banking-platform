package com.banking.upi.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class UpiTransactionResponse {
    private UUID id;
    private UUID transactionId;
    private String payerVpa;
    private String payeeVpa;
    private BigDecimal amount;
    private String remarks;
    private String status;
    private Instant createdAt;
}
