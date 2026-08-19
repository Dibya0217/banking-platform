package com.banking.account.service;

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
public class FraudEventConsumer {

    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "banking.fraud.events",
            groupId = "account-service-fraud-cg",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String message, Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.path("eventType").asText();

            if ("fraud.account.frozen".equals(eventType)) {
                handleAccountFrozen(node);
            } else {
                log.debug("Ignoring fraud event type={}", eventType);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process fraud event: {}", e.getMessage(), e);
        }
    }

    private void handleAccountFrozen(JsonNode node) {
        UUID accountId = UUID.fromString(node.path("accountId").asText());
        String reason = node.path("reason").asText("Fraud detection auto-freeze");
        log.warn("Freezing account {} due to fraud detection: {}", accountId, reason);
        accountService.freezeAccount(accountId, reason);
    }
}
