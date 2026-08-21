package com.banking.audit.controller;

import com.banking.audit.dto.response.AuditLogResponse;
import com.banking.audit.entity.AuditLog;
import com.banking.audit.service.AuditService;
import com.banking.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_AUDITOR')")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getLogs(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        Page<AuditLogResponse> page = auditService
                .getLogs(eventType, actorId, entityType, entityId, pageable)
                .map(this::toResponse);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .eventId(log.getEventId())
                .eventType(log.getEventType())
                .topic(log.getTopic())
                .actorId(log.getActorId())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
