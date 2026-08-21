package com.banking.audit.service;

import com.banking.audit.entity.AuditLog;
import com.banking.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void record(String topic, String eventType, String eventId,
                       UUID actorId, String entityType, String entityId, String rawPayload) {
        AuditLog log = AuditLog.builder()
                .eventId(eventId)
                .eventType(eventType)
                .topic(topic)
                .actorId(actorId)
                .entityType(entityType)
                .entityId(entityId)
                .payload(rawPayload)
                .build();
        auditLogRepository.save(log);
    }

    public Page<AuditLog> getLogs(String eventType, UUID actorId, String entityType, String entityId, Pageable pageable) {
        if (eventType != null) return auditLogRepository.findByEventType(eventType, pageable);
        if (actorId != null) return auditLogRepository.findByActorId(actorId, pageable);
        if (entityType != null && entityId != null)
            return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
        return auditLogRepository.findAll(pageable);
    }
}
