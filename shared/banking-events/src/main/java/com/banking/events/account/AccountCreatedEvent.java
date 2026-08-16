package com.banking.events.account;

import com.banking.events.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor
public class AccountCreatedEvent extends BaseEvent {

    private String accountId;
    private String customerId;
    private String accountNumber;
    private String accountType;
    private Instant createdAt;

    public static AccountCreatedEvent of(String accountId, String customerId,
                                          String accountNumber, String accountType,
                                          String correlationId) {
        return AccountCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("account.created")
                .eventVersion("1.0")
                .producedAt(Instant.now())
                .producerService("account-service")
                .correlationId(correlationId)
                .accountId(accountId)
                .customerId(customerId)
                .accountNumber(accountNumber)
                .accountType(accountType)
                .createdAt(Instant.now())
                .build();
    }
}
