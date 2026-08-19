package com.banking.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailedNotificationRetryScheduler {

    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 600_000) // every 10 minutes
    public void retryFailed() {
        log.info("Running failed notification retry sweep");
        notificationService.retryFailed();
    }
}
