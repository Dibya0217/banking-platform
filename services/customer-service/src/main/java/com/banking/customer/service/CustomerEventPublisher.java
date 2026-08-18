package com.banking.customer.service;

import com.banking.customer.entity.OutboxEvent;
import com.banking.customer.repository.OutboxRepository;
import com.banking.events.customer.CustomerFrozenEvent;
import com.banking.events.customer.CustomerKycApprovedEvent;
import com.banking.events.customer.CustomerRegisteredEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerEventPublisher {

    static final String TOPIC = "banking.customer.events";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void publishCustomerRegistered(CustomerRegisteredEvent event) {
        persist(event.getCustomerId(), event.getEventType(), event);
    }

    public void publishCustomerKycApproved(CustomerKycApprovedEvent event) {
        persist(event.getCustomerId(), event.getEventType(), event);
    }

    public void publishCustomerFrozen(CustomerFrozenEvent event) {
        persist(event.getCustomerId(), event.getEventType(), event);
    }

    private void persist(String aggregateId, String eventType, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = OutboxEvent.builder()
                    .topic(TOPIC)
                    .aggregateType("Customer")
                    .aggregateId(UUID.fromString(aggregateId))
                    .eventType(eventType)
                    .payload(payload)
                    .build();
            outboxRepository.save(outbox);
            log.debug("Outbox event persisted: type={}, aggregateId={}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event: " + eventType, e);
        }
    }
}
