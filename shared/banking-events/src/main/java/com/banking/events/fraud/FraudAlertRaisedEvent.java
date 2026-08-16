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
public class FraudAlertRaisedEvent extends BaseEvent {

    private String alertId;
    private String transactionId;
    private String accountId;
    private String ruleTriggered;
    private String severity;
    private boolean shouldBlock;
    private String description;
    private Instant raisedAt;

    public static FraudAlertRaisedEvent of(String alertId, String transactionId,
                                            String accountId, String ruleTriggered,
                                            String severity, boolean shouldBlock,
                                            String description, String correlationId) {
        return FraudAlertRaisedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("fraud.alert.raised")
                .eventVersion("1.0")
                .producedAt(Instant.now())
                .producerService("fraud-detection-service")
                .correlationId(correlationId)
                .alertId(alertId)
                .transactionId(transactionId)
                .accountId(accountId)
                .ruleTriggered(ruleTriggered)
                .severity(severity)
                .shouldBlock(shouldBlock)
                .description(description)
                .raisedAt(Instant.now())
                .build();
    }
}
