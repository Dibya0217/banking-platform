package com.banking.events.fraud;

import com.banking.events.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor
public class FraudCheckPassedEvent extends BaseEvent {

    private String transactionId;
    private String fromAccountId;
    private Instant checkedAt;

    public static FraudCheckPassedEvent of(String transactionId, String fromAccountId,
                                            String correlationId) {
        return FraudCheckPassedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("fraud.check.passed")
                .eventVersion("1.0")
                .producedAt(Instant.now())
                .producerService("fraud-detection-service")
                .correlationId(correlationId)
                .transactionId(transactionId)
                .fromAccountId(fromAccountId)
                .checkedAt(Instant.now())
                .build();
    }
}
