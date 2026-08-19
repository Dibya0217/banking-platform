package com.banking.notification.service.email;

import com.banking.notification.repository.NotificationRepository;
import com.banking.notification.service.BaseNotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailNotificationSender extends BaseNotificationSender {

    private final JavaMailSender mailSender;

    @Value("${notification.from-email:noreply@banking.com}")
    private String fromEmail;

    @Value("${notification.from-name:Banking Platform}")
    private String fromName;

    public EmailNotificationSender(NotificationRepository notificationRepository,
                                   JavaMailSender mailSender) {
        super(notificationRepository);
        this.mailSender = mailSender;
    }

    @Override
    protected void doSend(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromName + " <" + fromEmail + ">");
        message.setTo(recipient);
        message.setSubject(subject != null ? subject : "Banking Notification");
        message.setText(body);
        mailSender.send(message);
        log.debug("Email sent to {}", recipient);
    }
}
