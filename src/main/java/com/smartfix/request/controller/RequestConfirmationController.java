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

    @ModelAttribute("finalReview")
    public FinalReviewCommand finalReviewForm() { return new FinalReviewCommand(); }

    @PostMapping("/requests/{ticket}/confirm")
    public String confirm(
            @PathVariable String ticket, @AuthenticationPrincipal SmartFixUserDetails p,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        service.confirm(ticket, p.getUserId());
        redirect.addFlashAttribute("successMessage", "Repair accepted. The request is now awaiting final administrator review.");
        return redirect(ticket);
    }

    @PostMapping("/requests/{ticket}/close")
    public String close(
            @PathVariable String ticket, @AuthenticationPrincipal SmartFixUserDetails p,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        service.close(ticket, p.getUserId());
        redirect.addFlashAttribute("successMessage", "Final review approved. The request is closed.");
        return redirect(ticket);
    }

    @PostMapping("/requests/{ticket}/return-for-repair")
    public String returnForRepair(@PathVariable String ticket,
            @Valid @ModelAttribute("finalReview") FinalReviewCommand command,
            BindingResult errors, @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect,
            jakarta.servlet.http.HttpServletResponse response) {
        populateDetails(ticket, principal.getUserId(), model);
        if (errors.hasErrors()) {
            response.setStatus(400);
            model.addAttribute("finalReviewInvalid", true);
            return "request/detail";
        }
        service.returnForRepair(ticket, principal.getUserId(), command.getComment());
        redirect.addFlashAttribute("successMessage", "Returned for repair. The engineer can resume work; the repaired result must be accepted again.");
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
