package com.smartfix.request.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.dto.*;
import com.smartfix.request.service.RequestConfirmationService;
import com.smartfix.request.service.RequestPresentationService;
import com.smartfix.request.service.RequestQueryService;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class RequestConfirmationController {
    private final RequestConfirmationService service;
    private final RequestQueryService queries;
    private final RequestPresentationService presentation;

    public RequestConfirmationController(RequestConfirmationService service,
            RequestQueryService queries, RequestPresentationService presentation) {
        this.service = service;
        this.queries = queries;
        this.presentation = presentation;
    }

    @ModelAttribute("feedback")
    public RequestFeedbackCommand feedbackForm() {
        return new RequestFeedbackCommand();
    }

    @ModelAttribute("reopen")
    public RequestLifecycleActionCommand reopenForm() {
        return new RequestLifecycleActionCommand();
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
            @Valid @ModelAttribute("reopen") RequestLifecycleActionCommand c,
            BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails p,
            Model model) {
        populateDetails(ticket, p.getUserId(), model);
        if (errors.hasErrors()) return "request/detail";
        service.reopen(ticket, p.getUserId(), c.getComment());
        return redirect(ticket);
    }

    @PostMapping("/requests/{ticket}/feedback")
    public String feedback(
            @PathVariable String ticket,
            @Valid @ModelAttribute("feedback") RequestFeedbackCommand c,
            BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails p,
            Model model) {
        populateDetails(ticket, p.getUserId(), model);
        if (errors.hasErrors()) return "request/detail";
        service.feedback(ticket, p.getUserId(), c.getRating(), c.getComment());
        return redirect(ticket);
    }

    private void populateDetails(String ticket, Long actorId, Model model) {
        // Check ownership before rendering any validation errors for a private request.
        model.addAttribute("request", queries.getRequestDetails(ticket, actorId));
        model.addAttribute("presentation", presentation.describe(ticket, actorId));
    }

    private String redirect(String ticket) {
        return "redirect:/requests/" + ticket;
    }
}
