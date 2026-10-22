package com.smartfix.common.web;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.notification.service.NotificationService;
import org.springframework.beans.factory.ObjectProvider;
import java.security.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** A fresh, recipient-scoped count for every authenticated page render. */
@ControllerAdvice
public class NotificationNavigationAdvice {
    private final ObjectProvider<NotificationService> notifications;

    public NotificationNavigationAdvice(ObjectProvider<NotificationService> notifications) {
        this.notifications = notifications;
    }

    @ModelAttribute
    public void addUnreadCount(Model model, Principal principal) {
        long count = 0;
        if (principal instanceof Authentication authentication && authentication.getPrincipal() instanceof SmartFixUserDetails user) {
            var service = notifications.getIfAvailable();
            if (service != null) count = service.getUnreadCount(user.getUserId());
        }
        model.addAttribute("navUnreadCount", count);
    }
}
