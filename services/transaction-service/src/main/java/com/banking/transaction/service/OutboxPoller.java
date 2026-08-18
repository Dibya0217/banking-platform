package com.banking.transaction.service;

import com.banking.transaction.entity.OutboxEvent;
import com.banking.transaction.repository.OutboxRepository;
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
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 100)
    @Transactional
    public void poll() {
        List<OutboxEvent> events = outboxRepository.findUnpublishedWithLock();
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload());
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
