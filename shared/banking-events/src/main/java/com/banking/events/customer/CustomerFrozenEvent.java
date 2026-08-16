package com.banking.events.customer;

import com.banking.events.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor
public class CustomerFrozenEvent extends BaseEvent {

    private String customerId;
    private String reason;
    private String frozenBy;
    private Instant frozenAt;

    public static CustomerFrozenEvent of(String customerId, String reason,
                                          String frozenBy, String correlationId) {
        return CustomerFrozenEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("customer.frozen")
                .eventVersion("1.0")
                .producedAt(Instant.now())
                .producerService("customer-service")
                .correlationId(correlationId)
                .customerId(customerId)
                .reason(reason)
                .frozenBy(frozenBy)
                .frozenAt(Instant.now())
                .build();
    }
}