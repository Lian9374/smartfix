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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.PageRequest;

import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.notification.domain.Notification;
import com.smartfix.notification.repository.NotificationInsertRepository;
import com.smartfix.notification.repository.NotificationRepository;

class NotificationServiceTest {

    private NotificationRepository repository;
    private NotificationInsertRepository insertRepository;
    private NotificationService service;

    private static final Instant NOW =
            Instant.parse("2026-10-08T10:00:00Z");

    private static final String DEDUP_KEY =
            "REQUEST_STATUS_CHANGED:100:SUBMITTED:UNDER_REVIEW:"
                    + "2026-10-08T10:00:00Z:10";

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        insertRepository = mock(NotificationInsertRepository.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        service = new NotificationService(
                repository,
                insertRepository,
                clock
        );
    }

    @Test
    void firstEventCreatesNotification() {

        when(insertRepository.insertIfAbsent(
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                anyString(),
                any(Instant.class)
        )).thenReturn(true);

        boolean result = service.createNotification(
                10L,
                "REQUEST_STATUS_CHANGED",
                "Request Updated",
                "Your request status has changed.",
                100L,
                DEDUP_KEY
        );

        assertTrue(result);

        verify(insertRepository).insertIfAbsent(
                10L,
                "REQUEST_STATUS_CHANGED",
                "Request Updated",
                "Your request status has changed.",
                100L,
                DEDUP_KEY,
                NOW
        );
    }

    @Test
    void duplicateEventDoesNotCreateNotification() {

        when(insertRepository.insertIfAbsent(
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                anyString(),
                any(Instant.class)
        )).thenReturn(false);

        boolean result = service.createNotification(
                10L,
                "REQUEST_STATUS_CHANGED",
                "Request Updated",
                "Your request status has changed.",
                100L,
                DEDUP_KEY
        );

        assertFalse(result);
    }

    @Test
    void getNotificationsOnlyQueriesCurrentRecipient() {

        Notification notification = Notification.create(
                10L,
                "REQUEST_STATUS_CHANGED",
                "Request Updated",
                "Your request status has changed.",
                100L,
                NOW,
                DEDUP_KEY
        );

        when(repository.findAllByRecipientIdOrderByCreatedAtDesc(
                eq(10L),
                any(PageRequest.class)
        )).thenReturn(List.of(notification));

        List<Notification> result =
                service.getNotifications(10L);

        assertEquals(1, result.size());

        verify(repository)
                .findAllByRecipientIdOrderByCreatedAtDesc(
                        10L,
                        PageRequest.of(0, 20)
                );
    }

    @Test
    void getUnreadCountUsesRecipientId() {

        when(repository.countByRecipientIdAndReadAtIsNull(10L))
                .thenReturn(3L);

        assertEquals(3L, service.getUnreadCount(10L));
    }

    @Test
    void markAsReadUpdatesReadTimestamp() {

        Notification notification = Notification.create(
                10L,
                "REQUEST_STATUS_CHANGED",
                "Request Updated",
                "Your request status has changed.",
                100L,
                NOW.minusSeconds(3600),
                DEDUP_KEY
        );

        when(repository.findByIdAndRecipientId(5L, 10L))
                .thenReturn(Optional.of(notification));

        service.markAsRead(5L, 10L);

        assertEquals(NOW, notification.getReadAt());
    }

    @Test
    void markAsReadIsIdempotent() {

        Notification notification = Notification.create(
                10L,
                "REQUEST_STATUS_CHANGED",
                "Request Updated",
                "Your request status has changed.",
                100L,
                NOW.minusSeconds(3600),
                DEDUP_KEY
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

        verify(repository, never()).findById(5L);
    }

    @Test
    void invalidDedupKeyIsRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createNotification(
                        10L,
                        "REQUEST_STATUS_CHANGED",
                        "Request Updated",
                        "Request updated.",
                        100L,
                        ""
                )
        );

        verifyNoInteractions(insertRepository);
    }
}