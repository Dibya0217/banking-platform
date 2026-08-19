package com.banking.fraud.service;

import com.banking.fraud.rule.FraudCheckContext;
import com.banking.fraud.rule.FraudRuleResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudEventPublisher {

    private static final String TOPIC = "banking.fraud.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishFraudCheckPassed(FraudCheckContext context) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "fraud.check.passed");
        event.put("eventId", UUID.randomUUID().toString());
        event.put("transactionId", context.transactionId().toString());
        event.put("fromAccountId", context.fromAccountId().toString());
        event.put("producedAt", Instant.now().toString());

        send(context.transactionId().toString(), event);
        log.debug("Published fraud.check.passed for transactionId={}", context.transactionId());
    }

    public void publishFraudAlertRaised(FraudCheckContext context, List<FraudRuleResult> failures, boolean shouldBlock) {
        String highestSeverity = failures.stream()
                .filter(r -> !r.passed())
                .map(r -> r.severity().name())
                .reduce((a, b) -> compareSeverity(a, b) >= 0 ? a : b)
                .orElse("HIGH");

        String reasons = failures.stream()
                .filter(r -> !r.passed())
                .map(FraudRuleResult::reason)
                .reduce((a, b) -> a + "; " + b)
                .orElse("Unknown");

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "fraud.alert.raised");
        event.put("eventId", UUID.randomUUID().toString());
        event.put("transactionId", context.transactionId().toString());
        event.put("fromAccountId", context.fromAccountId().toString());
        event.put("severity", highestSeverity);
        event.put("reason", reasons);
        event.put("shouldBlock", shouldBlock);
        event.put("producedAt", Instant.now().toString());

        send(context.transactionId().toString(), event);
        log.info("Published fraud.alert.raised transactionId={} severity={} shouldBlock={}",
                context.transactionId(), highestSeverity, shouldBlock);
    }

    public void publishAccountFrozen(UUID accountId, String reason) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "fraud.account.frozen");
        event.put("eventId", UUID.randomUUID().toString());
        event.put("accountId", accountId.toString());
        event.put("reason", reason);
        event.put("producedAt", Instant.now().toString());

        send(accountId.toString(), event);
        log.info("Published fraud.account.frozen for accountId={}", accountId);
    }

    private void send(String key, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC, key, json);
        } catch (Exception e) {
            log.error("Failed to publish fraud event: {}", e.getMessage(), e);
        }
    }

    private int compareSeverity(String a, String b) {
        String[] order = {"LOW", "MEDIUM", "HIGH", "CRITICAL"};
        int ia = 0, ib = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(a)) ia = i;
            if (order[i].equals(b)) ib = i;
        }
        return Integer.compare(ia, ib);
    }
}
