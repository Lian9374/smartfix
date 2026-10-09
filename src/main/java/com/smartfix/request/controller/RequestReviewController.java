package com.smartfix.request.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.request.domain.UrgencyLevel;
import com.smartfix.request.dto.RequestReviewCommand;
import com.smartfix.request.service.*;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class RequestReviewController {
    private final RequestReviewService reviews;
    private final RequestQueryService queries;

    public RequestReviewController(RequestReviewService reviews, RequestQueryService queries) {
        this.reviews = reviews;
        this.queries = queries;
    }

    @ModelAttribute("urgencyLevels")
    public UrgencyLevel[] priorities() {
        return UrgencyLevel.values();
    }

    @GetMapping("/requests/{ticket}/review")
    public String form(
            @PathVariable String ticket,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model) {
        var r = queries.getRequestDetails(ticket, principal.getUserId());
        var command = new RequestReviewCommand();
        command.setFinalUrgencyLevel(r.urgencyLevel());
        model.addAttribute("request", r);
        model.addAttribute("command", command);
        return "request/review";
    }

    @PostMapping("/requests/{ticket}/review")
    public String review(
            @PathVariable String ticket,
            @Valid @ModelAttribute("command") RequestReviewCommand command,
            BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                reviews.review(
                        ticket,
                        command.getFinalUrgencyLevel(),
                        principal.getUserId(),
                        command.getReject(),
                        command.getComment());
                redirect.addFlashAttribute("successMessage", command.getReject() ? "Request rejected." : "Review saved. The request is ready for dispatch.");
                return "redirect:/requests/" + ticket;
            } catch (InputValidationException invalid) {
                errors.reject(
                        "review.invalid",
                        "A rejection reason is required and must be at most 500 characters.");
            }
        }
        model.addAttribute("request", queries.getRequestDetails(ticket, principal.getUserId()));
        return "request/review";
    }
}
