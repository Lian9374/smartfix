package com.smartfix.common.web;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.notification.service.NotificationService;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAuthenticationData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationNavigationAdviceTest {
    private final NotificationService notifications = mock(NotificationService.class);

    private NotificationNavigationAdvice advice(boolean includeService) {
        var beans = new StaticListableBeanFactory();
        if (includeService) beans.addBean("notifications", notifications);
        return new NotificationNavigationAdvice(beans.getBeanProvider(NotificationService.class));
    }

    private UsernamePasswordAuthenticationToken signedInUser() {
        var user = new SmartFixUserDetails(new UserAuthenticationData(
                7L, "alice", "hash", Role.REQUESTER, AccountStatus.ACTIVE, 0L));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void readsOnlyTheSignedInRecipientsUnreadCount() {
        when(notifications.getUnreadCount(7L)).thenReturn(3L);
        var model = new ExtendedModelMap();

        advice(true).addUnreadCount(model, signedInUser());

        assertEquals(3L, model.get("navUnreadCount"));
        verify(notifications).getUnreadCount(7L);
    }

    @Test
    void anonymousRequestsDoNotQueryNotifications() {
        var model = new ExtendedModelMap();

        advice(true).addUnreadCount(model, null);

        assertEquals(0L, model.get("navUnreadCount"));
        verifyNoInteractions(notifications);
    }

    @Test
    void plainPrincipalsDoNotQueryNotificationsOrBreakPageRendering() {
        var model = new ExtendedModelMap();

        advice(true).addUnreadCount(model, () -> "root.admin");

        assertEquals(0L, model.get("navUnreadCount"));
        verifyNoInteractions(notifications);
    }

    @Test
    void mvcSlicesWithoutNotificationServiceStillRender() {
        var model = new ExtendedModelMap();

        advice(false).addUnreadCount(model, signedInUser());

        assertEquals(0L, model.get("navUnreadCount"));
    }
}
