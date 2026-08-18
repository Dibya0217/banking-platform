package com.banking.notification.service.sms;

import com.banking.notification.repository.NotificationRepository;
import com.banking.notification.service.BaseNotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SmsNotificationSender extends BaseNotificationSender {

    public SmsNotificationSender(NotificationRepository notificationRepository) {
        super(notificationRepository);
    }

    @Override
    protected void doSend(String recipient, String subject, String body) {
        // Dev stub — in production wire Twilio/AWS SNS here
        log.info("[SMS STUB] To: {}, Body: {}", recipient, body);
    }
}
