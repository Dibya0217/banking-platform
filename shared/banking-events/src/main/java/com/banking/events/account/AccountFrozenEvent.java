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
public class AccountFrozenEvent extends BaseEvent {

    private String accountId;
    private String customerId;
    private String reason;
    private String freezeType;
    private Instant frozenAt;

    public static AccountFrozenEvent of(String accountId, String customerId,
                                         String reason, String freezeType,
                                         String correlationId) {
        return AccountFrozenEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("account.frozen")
                .eventVersion("1.0")
                .producedAt(Instant.now())
                .producerService("account-service")
                .correlationId(correlationId)
                .accountId(accountId)
                .customerId(customerId)
                .reason(reason)
                .freezeType(freezeType)
                .frozenAt(Instant.now())
                .build();
    }
}
