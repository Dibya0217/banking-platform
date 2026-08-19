package com.banking.notification.service;

import com.banking.notification.entity.Notification;
import com.banking.notification.entity.NotificationChannel;
import com.banking.notification.entity.NotificationStatus;
import com.banking.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationChannelFactory channelFactory;

    @Value("${notification.max-retries:3}")
    private int maxRetries;

    @Transactional
    public void send(UUID customerId, NotificationChannel channel, String recipient,
                     String subject, String body, String eventType) {
        Notification notification = Notification.builder()
                .customerId(customerId)
                .channel(channel)
                .recipient(recipient)
                .subject(subject)
                .body(body)
                .eventType(eventType)
                .status(NotificationStatus.PENDING)
                .build();

        notification = notificationRepository.save(notification);
        channelFactory.getSender(channel).send(notification);
    }

    @Transactional
    public void retryFailed() {
        List<Notification> retryable = notificationRepository.findRetryable(maxRetries);
        log.info("Retrying {} failed notifications", retryable.size());

        for (Notification notification : retryable) {
            if (notification.getRetryCount() >= maxRetries) {
                notification.setStatus(NotificationStatus.DEAD_LETTER);
                notificationRepository.save(notification);
                log.warn("Notification moved to DEAD_LETTER: id={}", notification.getId());
                continue;
            }
            channelFactory.getSender(notification.getChannel()).send(notification);
        }
    }
}
