package com.banking.transaction.dto.response;

import com.banking.transaction.entity.TransactionStatus;
import com.banking.transaction.entity.TransactionType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class TransactionResponse {

    private UUID id;
    private UUID fromAccountId;
    private UUID toAccountId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private String description;
    private String idempotencyKey;
    private String referenceNumber;
    private UUID reversalOf;
    private String failureReason;
    private UUID initiatedBy;
    private String channel;
    private Instant createdAt;
    private Instant completedAt;
}
