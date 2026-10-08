package com.smartfix.notification.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.notification.service.NotificationService;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(
            NotificationService notificationService
    ) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public String listNotifications(
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model
    ) {
        Long actorUserId = principal.getUserId();

        model.addAttribute(
                "notifications",
                notificationService.getNotifications(actorUserId)
        );

        model.addAttribute(
                "unreadCount",
                notificationService.getUnreadCount(actorUserId)
        );

        return "notification/list";
    }

    @PostMapping("/{notificationId}/read")
    public String markAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal SmartFixUserDetails principal
    ) {
        notificationService.markAsRead(
                notificationId,
                principal.getUserId()
        );

        return "redirect:/notifications";
    }
}
