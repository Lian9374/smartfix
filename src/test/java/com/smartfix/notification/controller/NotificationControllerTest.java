package com.smartfix.notification.controller;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.notification.service.NotificationService;

class NotificationControllerTest {

    private NotificationService notificationService;
    private NotificationController controller;
    private SmartFixUserDetails principal;

    @BeforeEach
    void setUp() {
        notificationService =
                mock(NotificationService.class);

        controller =
                new NotificationController(notificationService);

        principal =
                mock(SmartFixUserDetails.class);

        when(principal.getUserId())
                .thenReturn(10L);
    }

    @Test
    void listNotificationsUsesAuthenticatedUserId() {

        when(notificationService.getNotifications(10L))
                .thenReturn(List.of());

        when(notificationService.getUnreadCount(10L))
                .thenReturn(0L);

        Model model = new ExtendedModelMap();

        String view =
                controller.listNotifications(
                        principal,
                        model
                );

        assertEquals("notification/list", view);

        verify(notificationService)
                .getNotifications(10L);

        verify(notificationService)
                .getUnreadCount(10L);
    }

    @Test
    void markAsReadUsesAuthenticatedUserId() {

        String redirect =
                controller.markAsRead(
                        5L,
                        principal
                );

        assertEquals(
                "redirect:/notifications",
                redirect
        );

        verify(notificationService)
                .markAsRead(5L, 10L);
    }
}
