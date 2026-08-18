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
public class CustomerEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "banking.customer.events",
                   groupId = "notification-service-customer-cg",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.path("eventType").asText();

            switch (eventType) {
                case "customer.registered"    -> handleRegistered(node);
                case "customer.kyc.approved"  -> handleKycApproved(node);
                case "customer.kyc.rejected"  -> handleKycRejected(node);
                default -> log.debug("Ignoring customer event: {}", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process customer event: {}", e.getMessage(), e);
        }
    }

    private void handleRegistered(JsonNode node) {
        String customerId = node.path("customerId").asText(null);
        String email = node.path("email").asText("customer@placeholder.com");
        String name = node.path("firstName").asText("Customer");
        if (customerId == null) return;

        notificationService.send(
                UUID.fromString(customerId),
                NotificationChannel.EMAIL,
                email,
                "Welcome to Banking Platform!",
                "Dear " + name + ",\n\nWelcome! Your account has been registered successfully. "
                        + "Please complete KYC to activate all features.",
                "customer.registered"
        );
    }

    private void handleKycApproved(JsonNode node) {
        String customerId = node.path("customerId").asText(null);
        if (customerId == null) return;

        notificationService.send(
                UUID.fromString(customerId),
                NotificationChannel.EMAIL,
                "customer@placeholder.com",
                "KYC Approved — Account Activated",
                "Your KYC has been verified and approved. Your account is now fully active. "
                        + "A savings account has been automatically created for you.",
                "customer.kyc.approved"
        );
    }

    private void handleKycRejected(JsonNode node) {
        String customerId = node.path("customerId").asText(null);
        String reason = node.path("reason").asText("Please resubmit with valid documents.");
        if (customerId == null) return;

        notificationService.send(
                UUID.fromString(customerId),
                NotificationChannel.EMAIL,
                "customer@placeholder.com",
                "KYC Verification Failed",
                "Unfortunately, your KYC could not be verified. Reason: " + reason
                        + "\nPlease contact support for assistance.",
                "customer.kyc.rejected"
        );
    }
}
