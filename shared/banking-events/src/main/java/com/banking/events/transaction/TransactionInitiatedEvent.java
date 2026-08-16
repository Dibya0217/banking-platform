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
public class TransactionInitiatedEvent extends BaseEvent {

    private String transactionId;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private String initiatedBy;
    private String ipAddress;
    private String channel;
    private Instant initiatedAt;

    public static TransactionInitiatedEvent of(String transactionId, String fromAccountId,
                                                String toAccountId, BigDecimal amount,
                                                String transactionType, String initiatedBy,
                                                String channel, String correlationId) {
        return TransactionInitiatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("transaction.initiated")
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
                .initiatedBy(initiatedBy)
                .channel(channel)
                .initiatedAt(Instant.now())
                .build();
    }
}
