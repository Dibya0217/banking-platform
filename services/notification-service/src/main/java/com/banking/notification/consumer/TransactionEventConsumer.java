package com.banking.notification.consumer;

import com.banking.notification.entity.NotificationChannel;
import com.banking.notification.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "banking.transaction.events",
                   groupId = "notification-service-txn-cg",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.path("eventType").asText();

            switch (eventType) {
                case "transaction.completed" -> handleCompleted(node);
                case "transaction.failed"    -> handleFailed(node);
                default -> log.debug("Ignoring transaction event: {}", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process transaction event: {}", e.getMessage(), e);
        }
    }

    private void handleCompleted(JsonNode node) {
        String initiatedBy = node.path("initiatedBy").asText(null);
        String amount = node.path("amount").asText("?");
        String type = node.path("transactionType").asText("Transaction");
        String txnId = node.path("transactionId").asText("?");

        if (initiatedBy == null) return;

        // Extract email from event payload; transaction events carry initiatedBy (customerId) but no email.
        // TODO: resolve email from customer-service once an HTTP client is added.
        String email = node.path("email").asText(null);
        if (email == null || email.isBlank()) email = node.path("customerEmail").asText("noreply@banking.internal");

        String subject = type + " Successful";
        String body = "Your " + type.toLowerCase() + " of ₹" + amount
                + " has been completed successfully. Reference: " + txnId;

        notificationService.send(
                UUID.fromString(initiatedBy),
                NotificationChannel.EMAIL,
                email,
                subject, body, "transaction.completed"
        );
    }

    private void handleFailed(JsonNode node) {
        String initiatedBy = node.path("initiatedBy").asText(null);
        String amount = node.path("amount").asText("?");
        String reason = node.path("failureReason").asText("Unknown reason");
        String txnId = node.path("transactionId").asText("?");

        if (initiatedBy == null) return;

        // Extract email from event payload; transaction events carry initiatedBy (customerId) but no email.
        // TODO: resolve email from customer-service once an HTTP client is added.
        String email = node.path("email").asText(null);
        if (email == null || email.isBlank()) email = node.path("customerEmail").asText("noreply@banking.internal");

        String subject = "Transaction Failed";
        String body = "Your transaction of ₹" + amount + " failed. Reason: " + reason
                + ". Reference: " + txnId;

        notificationService.send(
                UUID.fromString(initiatedBy),
                NotificationChannel.EMAIL,
                email,
                subject, body, "transaction.failed"
        );
    }
}
