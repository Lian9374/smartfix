package com.smartfix.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.notification.domain.Notification;
import com.smartfix.notification.repository.NotificationRepository;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(
            NotificationRepository repository,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public Notification createNotification(
            Long recipientId,
            String eventType,
            String title,
            String message,
            Long referenceId
    ) {
        Notification notification = Notification.create(
                recipientId,
                eventType,
                title,
                message,
                referenceId,
                Instant.now(clock)
        );

        return repository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotifications(
            Long recipientId
    ) {
        return repository
                .findAllByRecipientIdOrderByCreatedAtDesc(
                        recipientId,
                        PageRequest.of(0, 20)
                );
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long recipientId) {
        return repository
                .countByRecipientIdAndReadAtIsNull(
                        recipientId
                );
    }

    @Transactional
    public void markAsRead(
            Long notificationId,
            Long recipientId
    ) {
        Notification notification = repository
                .findByIdAndRecipientId(
                        notificationId,
                        recipientId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Notification not found"
                        )
                );

        notification.markAsRead(Instant.now(clock));
    }
}