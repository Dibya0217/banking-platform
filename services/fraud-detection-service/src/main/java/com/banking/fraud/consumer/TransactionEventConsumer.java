package com.banking.fraud.consumer;

import com.banking.fraud.service.FraudDetectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final FraudDetectionService fraudDetectionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "banking.transaction.events",
                   groupId = "fraud-detection-service-txn-cg",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.path("eventType").asText();

            if ("transaction.initiated".equals(eventType)) {
                handleTransactionInitiated(node);
            } else {
                log.debug("Ignoring transaction event: {}", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process transaction event: {}", e.getMessage(), e);
        }
    }

    private void handleTransactionInitiated(JsonNode node) {
        UUID transactionId = UUID.fromString(node.path("transactionId").asText());
        UUID fromAccountId = UUID.fromString(node.path("fromAccountId").asText());
        String toAccountIdStr = node.path("toAccountId").asText(null);
        UUID toAccountId = toAccountIdStr != null && !toAccountIdStr.isBlank()
                ? UUID.fromString(toAccountIdStr) : null;
        BigDecimal amount = new BigDecimal(node.path("amount").asText("0"));
        String channel = node.path("channel").asText("API");

        log.debug("Running fraud check for transactionId={} fromAccountId={} amount={}",
                transactionId, fromAccountId, amount);

        fraudDetectionService.evaluateTransaction(transactionId, fromAccountId, toAccountId, amount, channel);
    }
}
