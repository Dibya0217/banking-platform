package com.banking.audit.consumer;

import com.banking.audit.service.AuditService;
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
public class AllEventConsumer {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {
                "banking.customer.events",
                "banking.account.events",
                "banking.transaction.events",
                "banking.fraud.events"
            },
            groupId = "audit-service-cg",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload String message,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.path("eventType").asText("unknown");
            String eventId   = node.path("eventId").asText(null);

            UUID actorId     = extractActorId(node, eventType);
            String entityType = deriveEntityType(eventType);
            String entityId   = extractEntityId(node, eventType);

            auditService.record(topic, eventType, eventId, actorId, entityType, entityId, message);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to audit event from topic={}: {}", topic, e.getMessage(), e);
            ack.acknowledge(); // always ack — audit must not block the pipeline
        }
    }

    private UUID extractActorId(JsonNode node, String eventType) {
        for (String field : new String[]{"customerId", "initiatedBy", "fromAccountId", "accountId"}) {
            String val = node.path(field).asText(null);
            if (val != null && !val.isBlank()) {
                try { return UUID.fromString(val); } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    private String deriveEntityType(String eventType) {
        if (eventType.startsWith("customer.")) return "CUSTOMER";
        if (eventType.startsWith("account."))  return "ACCOUNT";
        if (eventType.startsWith("transaction.")) return "TRANSACTION";
        if (eventType.startsWith("fraud."))    return "FRAUD";
        return "UNKNOWN";
    }

    private String extractEntityId(JsonNode node, String eventType) {
        for (String field : new String[]{"transactionId", "accountId", "customerId", "alertId"}) {
            String val = node.path(field).asText(null);
            if (val != null && !val.isBlank()) return val;
        }
        return null;
    }
}
