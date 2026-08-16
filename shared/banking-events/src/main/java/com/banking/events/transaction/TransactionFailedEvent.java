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
public class TransactionFailedEvent extends BaseEvent {

    private String transactionId;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String failureReason;
    private Instant failedAt;

    public static TransactionFailedEvent of(String transactionId, String fromAccountId,
                                             String toAccountId, BigDecimal amount,
                                             String failureReason, String correlationId) {
        return TransactionFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("transaction.failed")
                .eventVersion("1.0")
                .producedAt(Instant.now())
                .producerService("transaction-service")
                .correlationId(correlationId)
                .transactionId(transactionId)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .failureReason(failureReason)
                .failedAt(Instant.now())
                .build();
    }
}
