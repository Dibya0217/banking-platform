package com.banking.account.repository;

import com.banking.account.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM OutboxEvent o
            WHERE o.published = false
            ORDER BY o.createdAt ASC
            LIMIT 100
            """)
    List<OutboxEvent> findUnpublishedWithLock();
}
