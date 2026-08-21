package com.banking.audit.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AuditLogResponse {
    private UUID id;
    private String eventId;
    private String eventType;
    private String topic;
    private UUID actorId;
    private String entityType;
    private String entityId;
    private Instant createdAt;
}
