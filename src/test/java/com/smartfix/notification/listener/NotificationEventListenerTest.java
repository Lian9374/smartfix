package com.smartfix.notification.listener;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfix.notification.service.NotificationService;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.event.RequestStatusChangedEvent;
import com.smartfix.workorder.event.WorkOrderCompletedEvent;

class NotificationEventListenerTest {

    private NotificationService notificationService;
    private NotificationEventListener listener;

    private static final Instant NOW =
            Instant.parse("2026-10-08T10:00:00Z");

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        listener = new NotificationEventListener(notificationService);
    }

    @Test
    void requestStatusChangedNotifiesRequester() {

        RequestStatusChangedEvent event =
                new RequestStatusChangedEvent(
                        100L,
                        "SF-2026-000001",
                        10L,
                        RequestStatus.SUBMITTED,
                        RequestStatus.UNDER_REVIEW,
                        20L,
                        NOW
                );

        listener.on(event);

        verify(notificationService).createNotification(
                eq(10L),
                eq("REQUEST_STATUS_CHANGED"),
                eq("Maintenance Request Updated"),
                contains("SF-2026-000001"),
                eq(100L),
                startsWith("REQUEST_STATUS_CHANGED:100:")
        );
    }

    @Test
    void workOrderCompletedNotifiesRequester() {

        WorkOrderCompletedEvent event =
                new WorkOrderCompletedEvent(
                        50L,
                        100L,
                        "SF-2026-000001",
                        30L,
                        10L,
                        NOW
                );

        listener.on(event);

        verify(notificationService).createNotification(
                eq(10L),
                eq("WORK_ORDER_COMPLETED"),
                eq("Maintenance Work Completed"),
                contains("SF-2026-000001"),
                eq(100L),
                startsWith("WORK_ORDER_COMPLETED:50:")
        );
    }

    @Test
    void duplicateRequestEventGeneratesSameDedupKey() {

        RequestStatusChangedEvent event =
                new RequestStatusChangedEvent(
                        100L,
                        "SF-2026-000001",
                        10L,
                        RequestStatus.SUBMITTED,
                        RequestStatus.UNDER_REVIEW,
                        20L,
                        NOW
                );

        listener.on(event);
        listener.on(event);

        ArgumentCaptor<String> captor =
                ArgumentCaptor.forClass(String.class);

        verify(notificationService, times(2))
                .createNotification(
                        eq(10L),
                        eq("REQUEST_STATUS_CHANGED"),
                        anyString(),
                        anyString(),
                        eq(100L),
                        captor.capture()
                );

        assertEquals(
                captor.getAllValues().get(0),
                captor.getAllValues().get(1)
        );
    }

    @Test
    void duplicateWorkOrderEventGeneratesSameDedupKey() {

        WorkOrderCompletedEvent event =
                new WorkOrderCompletedEvent(
                        50L,
                        100L,
                        "SF-2026-000001",
                        30L,
                        10L,
                        NOW
                );

        listener.on(event);
        listener.on(event);

        ArgumentCaptor<String> captor =
                ArgumentCaptor.forClass(String.class);

        verify(notificationService, times(2))
                .createNotification(
                        eq(10L),
                        eq("WORK_ORDER_COMPLETED"),
                        anyString(),
                        anyString(),
                        eq(100L),
                        captor.capture()
                );

        assertEquals(
                captor.getAllValues().get(0),
                captor.getAllValues().get(1)
        );
    }

    @Test
    void notificationFailureDoesNotPropagate() {

        RequestStatusChangedEvent event =
                new RequestStatusChangedEvent(
                        100L,
                        "SF-2026-000001",
                        10L,
                        RequestStatus.SUBMITTED,
                        RequestStatus.UNDER_REVIEW,
                        20L,
                        NOW
                );

        when(notificationService.createNotification(
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                anyString()
        )).thenThrow(new IllegalStateException("Database unavailable"));

        assertDoesNotThrow(() -> listener.on(event));
    }
}