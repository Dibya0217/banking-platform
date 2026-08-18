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
public class AccountEventConsumer {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final TransactionEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "banking.account.events",
                   groupId = "transaction-service-account-cg",
                   containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void consume(String message, Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.path("eventType").asText();

            switch (eventType) {
                case "account.debited" -> handleAccountDebited(node);
                case "account.credited" -> handleAccountCredited(node);
                default -> log.debug("Ignoring event type: {}", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process account event: {}", e.getMessage(), e);
        }
    }

    private void handleAccountDebited(JsonNode node) {
        String transactionId = node.path("transactionId").asText(null);
        if (transactionId == null) return;

        transactionRepository.findById(UUID.fromString(transactionId)).ifPresent(txn -> {
            if (txn.getTransactionType() != TransactionType.TRANSFER) return;
            if (txn.getStatus() != TransactionStatus.PENDING && txn.getStatus() != TransactionStatus.DEBIT_PENDING) return;

            log.info("Transfer debit confirmed, crediting recipient: txnId={}", transactionId);
            txn.transitionTo(TransactionStatus.CREDIT_PENDING);
            transactionRepository.save(txn);

            try {
                accountServiceClient.credit(txn.getToAccountId(), txn.getAmount(), txn.getId().toString());
            } catch (WebClientResponseException e) {
                log.error("Credit failed during saga, marking txn failed: txnId={}", transactionId);
                txn.setFailureReason("Credit failed: " + e.getStatusCode());
                txn.transitionTo(TransactionStatus.FAILED);
                transactionRepository.save(txn);
                eventPublisher.publishFailed(txn, txn.getFailureReason());
            }
        });
    }

    private void handleAccountCredited(JsonNode node) {
        String transactionId = node.path("transactionId").asText(null);
        if (transactionId == null) return;

        transactionRepository.findById(UUID.fromString(transactionId)).ifPresent(txn -> {
            if (txn.getTransactionType() != TransactionType.TRANSFER) return;
            if (txn.getStatus() != TransactionStatus.CREDIT_PENDING) return;

            txn.transitionTo(TransactionStatus.COMPLETED);
            transactionRepository.save(txn);
            eventPublisher.publishCompleted(txn);
            log.info("Transfer completed via saga: txnId={}", transactionId);
        });
    }
}
