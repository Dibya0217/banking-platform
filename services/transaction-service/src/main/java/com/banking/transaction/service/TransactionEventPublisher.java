package com.banking.transaction.service;

import com.banking.events.transaction.TransactionCompletedEvent;
import com.banking.events.transaction.TransactionFailedEvent;
import com.banking.events.transaction.TransactionInitiatedEvent;
import com.banking.transaction.entity.OutboxEvent;
import com.banking.transaction.entity.Transaction;
import com.banking.transaction.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventPublisher {

    static final String TOPIC = "banking.transaction.events";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void publishInitiated(Transaction txn) {
        TransactionInitiatedEvent event = TransactionInitiatedEvent.of(
                txn.getId().toString(),
                txn.getFromAccountId() != null ? txn.getFromAccountId().toString() : null,
                txn.getToAccountId() != null ? txn.getToAccountId().toString() : null,
                txn.getAmount(),
                txn.getTransactionType().name(),
                txn.getInitiatedBy().toString(),
                txn.getChannel(),
                MDC.get("correlationId")
        );
        persist(txn.getId().toString(), event.getEventType(), event);
    }

    public void publishCompleted(Transaction txn) {
        TransactionCompletedEvent event = TransactionCompletedEvent.of(
                txn.getId().toString(),
                txn.getFromAccountId() != null ? txn.getFromAccountId().toString() : null,
                txn.getToAccountId() != null ? txn.getToAccountId().toString() : null,
                txn.getAmount(),
                txn.getTransactionType().name(),
                txn.getReferenceNumber(),
                txn.getInitiatedBy().toString(),
                MDC.get("correlationId")
        );
        persist(txn.getId().toString(), event.getEventType(), event);
    }

    public void publishFailed(Transaction txn, String reason) {
        TransactionFailedEvent event = TransactionFailedEvent.of(
                txn.getId().toString(),
                txn.getFromAccountId() != null ? txn.getFromAccountId().toString() : null,
                txn.getToAccountId() != null ? txn.getToAccountId().toString() : null,
                txn.getAmount(),
                reason,
                MDC.get("correlationId")
        );
        persist(txn.getId().toString(), event.getEventType(), event);
    }

    private void persist(String aggregateId, String eventType, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(OutboxEvent.builder()
                    .topic(TOPIC)
                    .aggregateType("Transaction")
                    .aggregateId(UUID.fromString(aggregateId))
                    .eventType(eventType)
                    .payload(payload)
                    .build());
            log.debug("Outbox event persisted: type={}, aggregateId={}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event: " + eventType, e);
        }
    }
}
