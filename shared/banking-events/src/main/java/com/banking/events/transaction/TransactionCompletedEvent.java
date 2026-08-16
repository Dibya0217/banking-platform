package com.banking.events.transaction;

import com.banking.events.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor
public class TransactionCompletedEvent extends BaseEvent {

    private String transactionId;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private String referenceNumber;
    private String initiatedBy;
    private Instant completedAt;

    public static TransactionCompletedEvent of(String transactionId, String fromAccountId,
                                                String toAccountId, BigDecimal amount,
                                                String transactionType, String referenceNumber,
                                                String initiatedBy, String correlationId) {
        return TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("transaction.completed")
                .eventVersion("1.0")
                .producedAt(Instant.now())
                .producerService("transaction-service")
                .correlationId(correlationId)
                .transactionId(transactionId)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .currency("INR")
                .transactionType(transactionType)
                .referenceNumber(referenceNumber)
                .initiatedBy(initiatedBy)
                .completedAt(Instant.now())
                .build();
    }
}
