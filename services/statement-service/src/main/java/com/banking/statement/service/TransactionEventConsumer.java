package com.banking.statement.service;

import com.banking.statement.entity.StatementTransaction;
import com.banking.statement.repository.StatementTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final StatementTransactionRepository statementTransactionRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "banking.transaction.events",
            groupId = "statement-service-txn-cg",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String message, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.path("eventType").asText();

            if ("transaction.completed".equals(eventType)) {
                processCompletedEvent(root);
            } else {
                log.debug("Ignoring event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing Kafka message: {}", e.getMessage(), e);
        } finally {
            ack.acknowledge();
        }
    }

    private void processCompletedEvent(JsonNode root) {
        String transactionIdStr = root.path("transactionId").asText(null);
        if (transactionIdStr == null || transactionIdStr.isBlank()) {
            log.warn("Missing transactionId in event, skipping");
            return;
        }

        UUID transactionId;
        try {
            transactionId = UUID.fromString(transactionIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid transactionId format: {}", transactionIdStr);
            return;
        }

        // Skip if already processed (idempotency via unique constraint)
        if (statementTransactionRepository.existsByTransactionId(transactionId)) {
            log.debug("Transaction {} already processed, skipping", transactionId);
            return;
        }

        String fromAccountIdStr = root.path("fromAccountId").asText(null);
        String toAccountIdStr = root.path("toAccountId").asText(null);
        BigDecimal amount = new BigDecimal(root.path("amount").asText("0"));
        String transactionType = root.path("transactionType").asText(null);
        String description = root.path("description").asText(null);
        String referenceNumber = root.path("referenceNumber").asText(null);

        String completedAtStr = root.path("completedAt").asText(null);
        Instant transactedAt = (completedAtStr != null && !completedAtStr.isBlank())
                ? Instant.parse(completedAtStr)
                : Instant.now();

        // DEBIT for fromAccountId
        if (fromAccountIdStr != null && !fromAccountIdStr.isBlank() && !fromAccountIdStr.equals("null")) {
            try {
                UUID fromAccountId = UUID.fromString(fromAccountIdStr);
                StatementTransaction debitTxn = StatementTransaction.builder()
                        .accountId(fromAccountId)
                        .transactionId(transactionId)
                        .transactionType(transactionType)
                        .amount(amount)
                        .direction("DEBIT")
                        .description(description)
                        .referenceNumber(referenceNumber)
                        .transactedAt(transactedAt)
                        .build();
                statementTransactionRepository.save(debitTxn);
                log.debug("Saved DEBIT transaction {} for account {}", transactionId, fromAccountId);
            } catch (Exception e) {
                log.warn("Failed to save DEBIT record for transaction {}: {}", transactionId, e.getMessage());
            }
        }

        // CREDIT for toAccountId (use a different UUID if both exist since transaction_id has unique constraint)
        if (toAccountIdStr != null && !toAccountIdStr.isBlank() && !toAccountIdStr.equals("null")) {
            try {
                UUID toAccountId = UUID.fromString(toAccountIdStr);
                // For transfers, fromAccountId's record already used the transactionId
                // For credit-only (deposit), use the same transactionId
                boolean needNewId = fromAccountIdStr != null && !fromAccountIdStr.isBlank()
                        && !fromAccountIdStr.equals("null");

                StatementTransaction creditTxn = StatementTransaction.builder()
                        .accountId(toAccountId)
                        .transactionId(needNewId ? UUID.randomUUID() : transactionId)
                        .transactionType(transactionType)
                        .amount(amount)
                        .direction("CREDIT")
                        .description(description)
                        .referenceNumber(referenceNumber)
                        .transactedAt(transactedAt)
                        .build();
                statementTransactionRepository.save(creditTxn);
                log.debug("Saved CREDIT transaction for account {}", toAccountId);
            } catch (Exception e) {
                log.warn("Failed to save CREDIT record for transaction {}: {}", transactionId, e.getMessage());
            }
        }
    }
}
