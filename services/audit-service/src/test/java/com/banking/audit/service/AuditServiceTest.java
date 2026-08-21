package com.banking.audit.service;

import com.banking.audit.entity.AuditLog;
import com.banking.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @InjectMocks private AuditService auditService;

    @Test
    void record_savesAuditLogWithCorrectFields() {
        UUID actorId = UUID.randomUUID();
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditService.record("banking.transaction.events", "transaction.completed",
                "evt-001", actorId, "TRANSACTION", "txn-123", "{\"amount\":100}");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("transaction.completed");
        assertThat(saved.getActorId()).isEqualTo(actorId);
        assertThat(saved.getEntityType()).isEqualTo("TRANSACTION");
        assertThat(saved.getEntityId()).isEqualTo("txn-123");
    }

    @Test
    void getLogs_byEventType_delegatesToRepository() {
        when(auditLogRepository.findByEventType(eq("transaction.completed"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        auditService.getLogs("transaction.completed", null, null, null, Pageable.unpaged());

        verify(auditLogRepository).findByEventType(eq("transaction.completed"), any(Pageable.class));
    }

    @Test
    void getLogs_noFilter_returnsAll() {
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        auditService.getLogs(null, null, null, null, Pageable.unpaged());

        verify(auditLogRepository).findAll(any(Pageable.class));
    }
}
