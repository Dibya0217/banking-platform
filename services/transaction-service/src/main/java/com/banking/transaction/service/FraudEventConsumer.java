package com.banking.transaction.service;

import com.banking.transaction.entity.Transaction;
import com.banking.transaction.entity.TransactionStatus;
import com.banking.transaction.entity.TransactionType;
import com.banking.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FraudEventConsumer {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final TransactionEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "banking.fraud.events",
                   groupId = "transaction-service-fraud-cg",
                   containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void consume(String message, Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.path("eventType").asText();

            switch (eventType) {
                case "fraud.check.passed" -> handleFraudPassed(node);
                case "fraud.alert.raised" -> handleFraudAlert(node);
                default -> log.debug("Ignoring fraud event type: {}", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process fraud event: {}", e.getMessage(), e);
        }
    }

    private void handleFraudPassed(JsonNode node) {
        String transactionId = node.path("transactionId").asText(null);
        if (transactionId == null) return;

        transactionRepository.findById(UUID.fromString(transactionId)).ifPresent(txn -> {
            if (txn.getStatus() != TransactionStatus.FRAUD_CHECKING) return;

            log.info("Fraud check passed, proceeding with debit: txnId={}", transactionId);
            txn.transitionTo(TransactionStatus.DEBIT_PENDING);
            transactionRepository.save(txn);

            try {
                accountServiceClient.debit(txn.getFromAccountId(), txn.getAmount(), txn.getId().toString());
            } catch (WebClientResponseException e) {
                txn.setFailureReason("Debit failed after fraud check: " + e.getStatusCode());
                txn.transitionTo(TransactionStatus.FAILED);
                transactionRepository.save(txn);
                eventPublisher.publishFailed(txn, txn.getFailureReason());
            }
        });
    }

    private void handleFraudAlert(JsonNode node) {
        String transactionId = node.path("transactionId").asText(null);
        boolean shouldBlock = node.path("shouldBlock").asBoolean(false);
        if (transactionId == null) return;

        transactionRepository.findById(UUID.fromString(transactionId)).ifPresent(txn -> {
            if (txn.getStatus() != TransactionStatus.FRAUD_CHECKING) return;

            if (shouldBlock) {
                log.warn("Fraud alert blocking transaction: txnId={}", transactionId);
                txn.setFailureReason("Blocked by fraud detection");
                txn.transitionTo(TransactionStatus.FAILED);
                eventPublisher.publishFailed(txn, txn.getFailureReason());
            } else {
                log.warn("Fraud alert flagged for manual review: txnId={}", transactionId);
                txn.transitionTo(TransactionStatus.MANUAL_REVIEW);
            }
            transactionRepository.save(txn);
        });
    }
}
