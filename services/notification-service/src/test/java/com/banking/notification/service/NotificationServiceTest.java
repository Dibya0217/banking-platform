package com.banking.notification.service;

import com.banking.notification.entity.Notification;
import com.banking.notification.entity.NotificationChannel;
import com.banking.notification.entity.NotificationStatus;
import com.banking.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationChannelFactory channelFactory;
    @Mock private BaseNotificationSender mockSender;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, channelFactory);
        ReflectionTestUtils.setField(notificationService, "maxRetries", 3);
    }

    @Test
    void send_shouldPersistAndDelegate() {
        UUID customerId = UUID.randomUUID();
        Notification saved = Notification.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .channel(NotificationChannel.EMAIL)
                .recipient("user@test.com")
                .body("Hello")
                .build();

        given(notificationRepository.save(any())).willReturn(saved);
        given(channelFactory.getSender(NotificationChannel.EMAIL)).willReturn(mockSender);

        notificationService.send(customerId, NotificationChannel.EMAIL,
                "user@test.com", "Subject", "Hello", "test.event");

        verify(notificationRepository).save(any(Notification.class));
        verify(mockSender).send(saved);
    }

    @Test
    void retryFailed_withRetryableNotifications_shouldRetry() {
        Notification failed = Notification.builder()
                .id(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .channel(NotificationChannel.SMS)
                .recipient("+91XXXXXXXXXX")
                .body("Alert!")
                .status(NotificationStatus.FAILED)
                .retryCount(1)
                .build();

        given(notificationRepository.findRetryable(3)).willReturn(List.of(failed));
        given(channelFactory.getSender(NotificationChannel.SMS)).willReturn(mockSender);

        notificationService.retryFailed();

        verify(mockSender).send(failed);
    }

    @Test
    void retryFailed_whenMaxRetriesReached_shouldMoveToDead_Letter() {
        Notification exhausted = Notification.builder()
                .id(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .channel(NotificationChannel.EMAIL)
                .recipient("user@test.com")
                .body("Failed message")
                .status(NotificationStatus.FAILED)
                .retryCount(3)
                .build();

        given(notificationRepository.findRetryable(3)).willReturn(List.of(exhausted));

        notificationService.retryFailed();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.DEAD_LETTER);
        verifyNoInteractions(channelFactory);
    }
}
