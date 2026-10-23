package com.smartfix.common.web;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.notification.service.NotificationService;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAuthenticationData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NotificationNavigationAdviceTest {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<NotificationService> provider = mock(ObjectProvider.class);
    private final NotificationNavigationAdvice advice = new NotificationNavigationAdvice(provider);

    @Test
    void genericPrincipalDoesNotQueryNotificationStorage() {
        var model = new ExtendedModelMap();
        advice.addUnreadCount(model, () -> "root.admin");
        assertThat(model.get("navUnreadCount")).isEqualTo(0L);
        verifyNoInteractions(provider);
    }

    @Test
    void authenticatedSmartFixPrincipalGetsRecipientScopedCount() {
        var service = mock(NotificationService.class);
        when(provider.getIfAvailable()).thenReturn(service);
        when(service.getUnreadCount(7L)).thenReturn(3L);
        var details = new SmartFixUserDetails(new UserAuthenticationData(
                7L, "alice", "hash", Role.REQUESTER, AccountStatus.ACTIVE, 0L));
        var model = new ExtendedModelMap();
        advice.addUnreadCount(model, new UsernamePasswordAuthenticationToken(details, "unused"));
        assertThat(model.get("navUnreadCount")).isEqualTo(3L);
        verify(service).getUnreadCount(7L);
        verifyNoMoreInteractions(service);
    }
}
