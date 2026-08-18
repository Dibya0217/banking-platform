package com.banking.notification.service.push;

import com.banking.notification.repository.NotificationRepository;
import com.banking.notification.service.BaseNotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PushNotificationSender extends BaseNotificationSender {

    public PushNotificationSender(NotificationRepository notificationRepository) {
        super(notificationRepository);
    }

    @Override
    protected void doSend(String recipient, String subject, String body) {
        // Dev stub — in production wire FCM / APNs here
        log.info("[PUSH STUB] Token: {}, Title: {}, Body: {}", recipient, subject, body);
    }
}
