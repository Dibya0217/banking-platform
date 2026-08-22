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
public class CustomerKycRejectedEvent extends BaseEvent {

    private String customerId;
    private String kycId;
    private String reason;
    private String rejectedBy;
    private Instant rejectedAt;

    public static CustomerKycRejectedEvent of(String customerId, String kycId,
                                               String reason, String rejectedBy,
                                               String correlationId) {
        return CustomerKycRejectedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("customer.kyc.rejected")
                .eventVersion("1.0")
                .producedAt(Instant.now())
                .producerService("customer-service")
                .correlationId(correlationId)
                .customerId(customerId)
                .kycId(kycId)
                .reason(reason)
                .rejectedBy(rejectedBy)
                .rejectedAt(Instant.now())
                .build();
    }
}
