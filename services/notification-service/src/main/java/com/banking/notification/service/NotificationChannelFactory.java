package com.banking.notification.service;

import com.banking.notification.entity.NotificationChannel;
import com.banking.notification.service.email.EmailNotificationSender;
import com.banking.notification.service.push.PushNotificationSender;
import com.banking.notification.service.sms.SmsNotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationChannelFactory {

    private final EmailNotificationSender emailSender;
    private final SmsNotificationSender smsSender;
    private final PushNotificationSender pushSender;

    public BaseNotificationSender getSender(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailSender;
            case SMS -> smsSender;
            case PUSH -> pushSender;
        };
    }
}
