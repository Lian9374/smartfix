package com.smartfix.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.PageRequest;

import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.notification.domain.Notification;
import com.smartfix.notification.repository.NotificationRepository;

class NotificationServiceTest {

    private NotificationRepository repository;
    private NotificationService service;

    private static final Instant NOW =
            Instant.parse("2026-10-08T10:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);

        Clock clock = Clock.fixed(
                NOW,
                ZoneOffset.UTC
        );

        service = new NotificationService(
                repository,
                clock
        );
    }

    @Test
    void createNotificationSavesCorrectRecipient() {

        when(repository.save(any(Notification.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        Notification result = service.createNotification(
                10L,
                "REQUEST_STATUS_CHANGED",
                "Request Updated",
                "Your request status has changed.",
                100L
        );

        assertEquals(10L, result.getRecipientId());
        assertEquals(
                "REQUEST_STATUS_CHANGED",
                result.getEventType()
        );
        assertEquals("Request Updated", result.getTitle());
        assertEquals(100L, result.getReferenceId());
        assertEquals(NOW, result.getCreatedAt());
        assertFalse(result.isRead());

        verify(repository).save(any(Notification.class));
    }

    @Test
    void getNotificationsOnlyQueriesCurrentRecipient() {

        Notification notification = Notification.create(
                10L,
                "REQUEST_STATUS_CHANGED",
                "Request Updated",
                "Your request status has changed.",
                100L,
                NOW
        );

        when(repository.findAllByRecipientIdOrderByCreatedAtDesc(
                eq(10L),
                any(PageRequest.class)
        )).thenReturn(List.of(notification));

        List<Notification> result =
                service.getNotifications(10L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getRecipientId());

        verify(repository)
                .findAllByRecipientIdOrderByCreatedAtDesc(
                        eq(10L),
                        eq(PageRequest.of(0, 20))
                );
    }

    @Test
    void getUnreadCountUsesRecipientId() {

        when(repository.countByRecipientIdAndReadAtIsNull(10L))
                .thenReturn(3L);

        long count = service.getUnreadCount(10L);

        assertEquals(3L, count);

        verify(repository)
                .countByRecipientIdAndReadAtIsNull(10L);
    }

    @Test
    void markAsReadUpdatesReadTimestamp() {

        Notification notification = Notification.create(
                10L,
                "REQUEST_STATUS_CHANGED",
                "Request Updated",
                "Your request status has changed.",
                100L,
                NOW.minusSeconds(3600)
        );

        when(repository.findByIdAndRecipientId(5L, 10L))
                .thenReturn(Optional.of(notification));

        service.markAsRead(5L, 10L);

        assertTrue(notification.isRead());
        assertEquals(NOW, notification.getReadAt());

        verify(repository)
                .findByIdAndRecipientId(5L, 10L);
    }

    @Test
    void markAsReadIsIdempotent() {

        Notification notification = Notification.create(
                10L,
                "REQUEST_STATUS_CHANGED",
                "Request Updated",
                "Your request status has changed.",
                100L,
                NOW.minusSeconds(3600)
        );

        when(repository.findByIdAndRecipientId(5L, 10L))
                .thenReturn(Optional.of(notification));

        service.markAsRead(5L, 10L);

        Instant firstReadAt = notification.getReadAt();

        service.markAsRead(5L, 10L);

        assertEquals(firstReadAt, notification.getReadAt());
    }

    @Test
    void cannotMarkAnotherUsersNotificationAsRead() {

        when(repository.findByIdAndRecipientId(5L, 10L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.markAsRead(5L, 10L)
        );

        verify(repository, never())
                .findById(5L);
    }
}
