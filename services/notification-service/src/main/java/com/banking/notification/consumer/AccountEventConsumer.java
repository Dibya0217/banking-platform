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
public class AccountEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "banking.account.events",
                   groupId = "notification-service-account-cg",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.path("eventType").asText();

            if ("account.frozen".equals(eventType)) {
                handleAccountFrozen(node);
            } else if ("account.created".equals(eventType)) {
                handleAccountCreated(node);
            } else {
                log.debug("Ignoring account event: {}", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process account event: {}", e.getMessage(), e);
        }
    }

    private void handleAccountFrozen(JsonNode node) {
        String customerId = node.path("customerId").asText(null);
        String accountNumber = node.path("accountNumber").asText("your account");
        if (customerId == null) return;

        // Extract contact details from event payload.
        // TODO: resolve email/mobile from customer-service once an HTTP client is added.
        String email = node.path("email").asText(null);
        if (email == null || email.isBlank()) email = node.path("customerEmail").asText("noreply@banking.internal");
        String mobile = node.path("mobile").asText(null);
        if (mobile == null || mobile.isBlank()) mobile = node.path("customerMobile").asText("0000000000");

        notificationService.send(
                UUID.fromString(customerId),
                NotificationChannel.EMAIL,
                email,
                "Account Frozen — Action Required",
                "Your account ending " + accountNumber + " has been frozen. "
                        + "Please contact our support team immediately.",
                "account.frozen"
        );

        notificationService.send(
                UUID.fromString(customerId),
                NotificationChannel.SMS,
                mobile,
                null,
                "ALERT: Your bank account has been frozen. Contact support immediately.",
                "account.frozen"
        );
    }

    private void handleAccountCreated(JsonNode node) {
        String customerId = node.path("customerId").asText(null);
        String accountNumber = node.path("accountNumber").asText("?");
        if (customerId == null) return;

        // Extract email from event payload.
        // TODO: resolve email from customer-service once an HTTP client is added.
        String email = node.path("email").asText(null);
        if (email == null || email.isBlank()) email = node.path("customerEmail").asText("noreply@banking.internal");

        notificationService.send(
                UUID.fromString(customerId),
                NotificationChannel.EMAIL,
                email,
                "Account Created Successfully",
                "Your new bank account " + accountNumber + " has been created. "
                        + "You can now start transacting.",
                "account.created"
        );
    }
}
