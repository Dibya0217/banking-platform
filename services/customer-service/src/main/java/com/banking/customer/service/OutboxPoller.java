package com.banking.customer.service;

import com.banking.customer.entity.OutboxEvent;
import com.banking.customer.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 100)
    @Transactional
    public void poll() {
        List<OutboxEvent> events = outboxRepository.findUnpublishedWithLock();
        if (events.isEmpty()) return;

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to publish outbox event id={} type={}", event.getId(), event.getEventType(), ex);
                            }
                        });
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
            } catch (Exception e) {
                log.error("Error processing outbox event id={}", event.getId(), e);
            }
        }
    }
}
