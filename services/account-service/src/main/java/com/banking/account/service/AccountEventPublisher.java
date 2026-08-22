package com.banking.account.service;

import com.banking.account.entity.OutboxEvent;
import com.banking.account.repository.OutboxRepository;
import com.banking.events.account.AccountCreatedEvent;
import com.banking.events.account.AccountCreditedEvent;
import com.banking.events.account.AccountDebitedEvent;
import com.banking.events.account.AccountFrozenEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountEventPublisher {

    static final String TOPIC = "banking.account.events";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void publishAccountCreated(AccountCreatedEvent event) {
        persist(event.getAccountId(), event.getEventType(), event);
    }

    public void publishAccountDebited(AccountDebitedEvent event) {
        persist(event.getAccountId(), event.getEventType(), event);
    }

    public void publishAccountCredited(AccountCreditedEvent event) {
        persist(event.getAccountId(), event.getEventType(), event);
    }

    public void publishAccountFrozen(String accountId, String customerId,
                                      String reason, String freezeType) {
        AccountFrozenEvent event = AccountFrozenEvent.of(accountId, customerId, reason, freezeType, null);
        persist(event.getAccountId(), event.getEventType(), event);
    }

    private void persist(String aggregateId, String eventType, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(OutboxEvent.builder()
                    .topic(TOPIC)
                    .aggregateType("Account")
                    .aggregateId(UUID.fromString(aggregateId))
                    .eventType(eventType)
                    .payload(payload)
                    .build());
            log.debug("Outbox event persisted: type={}, aggregateId={}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event: " + eventType, e);
        }
    }
}
