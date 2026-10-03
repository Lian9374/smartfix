package com.smartfix.request.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.dto.*;
import com.smartfix.request.service.RequestConfirmationService;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class RequestConfirmationController {
    private final RequestConfirmationService service;

    public RequestConfirmationController(RequestConfirmationService service) {
        this.service = service;
    }

    @PostMapping("/requests/{ticket}/confirm")
    public String confirm(
            @PathVariable String ticket, @AuthenticationPrincipal SmartFixUserDetails p) {
        service.confirm(ticket, p.getUserId());
        return redirect(ticket);
    }

    @PostMapping("/requests/{ticket}/close")
    public String close(
            @PathVariable String ticket, @AuthenticationPrincipal SmartFixUserDetails p) {
        service.close(ticket, p.getUserId());
        return redirect(ticket);
    }

    @PostMapping("/requests/{ticket}/cancel")
    public String cancel(
            @PathVariable String ticket, @AuthenticationPrincipal SmartFixUserDetails p) {
        service.cancel(ticket, p.getUserId());
        return redirect(ticket);
    }

    @PostMapping("/requests/{ticket}/reopen")
    public String reopen(
            @PathVariable String ticket,
            @Valid @ModelAttribute RequestLifecycleActionCommand c,
            @AuthenticationPrincipal SmartFixUserDetails p) {
        service.reopen(ticket, p.getUserId(), c.getComment());
        return redirect(ticket);
    }

    @PostMapping("/requests/{ticket}/feedback")
    public String feedback(
            @PathVariable String ticket,
            @Valid @ModelAttribute RequestFeedbackCommand c,
            @AuthenticationPrincipal SmartFixUserDetails p) {
        service.feedback(ticket, p.getUserId(), c.getRating(), c.getComment());
        return redirect(ticket);
    }

    private String redirect(String ticket) {
        return "redirect:/requests/" + ticket;
    }
}
