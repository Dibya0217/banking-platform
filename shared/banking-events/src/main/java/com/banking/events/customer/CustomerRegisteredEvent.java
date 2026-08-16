package com.banking.events.customer;

import com.banking.events.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@SuperBuilder
@NoArgsConstructor
public class CustomerRegisteredEvent extends BaseEvent {

    private String customerId;
    private String fullName;
    private String email;
    private String mobile;
    private Instant registeredAt;

    public static CustomerRegisteredEvent of(String customerId, String fullName, String email,
                                              String mobile, String correlationId) {
        return CustomerRegisteredEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType("customer.registered")
                .eventVersion("1.0")
                .producedAt(Instant.now())
                .producerService("customer-service")
                .correlationId(correlationId)
                .customerId(customerId)
                .fullName(fullName)
                .email(email)
                .mobile(mobile)
                .registeredAt(Instant.now())
                .build();
    }
}
