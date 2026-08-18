package com.banking.account.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", schema = "account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;
}
