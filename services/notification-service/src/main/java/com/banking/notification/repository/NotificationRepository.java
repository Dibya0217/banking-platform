package com.banking.notification.repository;

import com.banking.notification.entity.Notification;
import com.banking.notification.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("SELECT n FROM Notification n WHERE n.status = 'FAILED' AND n.retryCount < :maxRetries ORDER BY n.createdAt ASC")
    List<Notification> findRetryable(int maxRetries);
}
