package com.banking.notification.service;

import com.banking.notification.entity.Notification;
import com.banking.notification.entity.NotificationStatus;
import com.banking.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseNotificationSender {

    protected final NotificationRepository notificationRepository;

    // Template Method — subclasses implement doSend()
    public final void send(Notification notification) {
        try {
            String formattedBody = format(notification);
            doSend(notification.getRecipient(), notification.getSubject(), formattedBody);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            log.info("Notification sent: id={}, channel={}, recipient={}", notification.getId(), notification.getChannel(), notification.getRecipient());
        } catch (Exception e) {
            log.error("Failed to send notification id={}: {}", notification.getId(), e.getMessage());
            notification.setRetryCount(notification.getRetryCount() + 1);
            notification.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "Unknown error");
            notification.setStatus(NotificationStatus.FAILED);
        } finally {
            notificationRepository.save(notification);
        }
    }

    protected String format(Notification notification) {
        return notification.getBody();
    }

    protected abstract void doSend(String recipient, String subject, String body) throws Exception;
}
