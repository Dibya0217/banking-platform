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
public class CustomerKycApprovedEvent extends BaseEvent {

    private String customerId;
    private String kycId;
    private String approvedBy;
    private Instant approvedAt;

    public static CustomerKycApprovedEvent of(String customerId, String kycId,
                                               String approvedBy, String correlationId) {
        return CustomerKycApprovedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("customer.kyc.approved")
                .eventVersion("1.0")
                .producedAt(Instant.now())
                .producerService("customer-service")
                .correlationId(correlationId)
                .customerId(customerId)
                .kycId(kycId)
                .approvedBy(approvedBy)
                .approvedAt(Instant.now())
                .build();
    }
}
