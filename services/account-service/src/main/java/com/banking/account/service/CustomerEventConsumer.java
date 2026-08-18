package com.banking.account.service;

import com.banking.account.dto.request.CreateAccountRequest;
import com.banking.account.entity.AccountType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerEventConsumer {

    private static final String KYC_APPROVED = "customer.kyc.approved";

    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "banking.customer.events",
            groupId = "account-service-customer-cg",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload String message,
                        @Header(KafkaHeaders.RECEIVED_KEY) String key,
                        Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.path("eventType").asText();

            if (KYC_APPROVED.equals(eventType)) {
                handleKycApproved(node);
            } else {
                log.debug("Ignoring customer event type={}", eventType);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process customer event key={}: {}", key, e.getMessage(), e);
            // Do not ack — let Kafka retry (or DLQ in production)
        }
    }

    private void handleKycApproved(JsonNode node) {
        UUID customerId = UUID.fromString(node.path("customerId").asText());
        log.info("Received customer.kyc.approved for customerId={} — auto-creating SAVINGS account", customerId);

        CreateAccountRequest request = new CreateAccountRequest(customerId, AccountType.SAVINGS);
        accountService.createAccount(request);
    }
}
