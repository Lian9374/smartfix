package com.smartfix.request.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.dto.MaintenanceRequestDetailsResponse;
import com.smartfix.request.dto.RequestFeedbackCommand;
import com.smartfix.request.dto.RequestLifecycleActionCommand;
import com.smartfix.request.service.RequestPresentationService;
import com.smartfix.request.service.RequestQueryService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RequestQueryController {

    private final RequestQueryService requestQueryService;

    private final RequestPresentationService presentation;

    public RequestQueryController(
            RequestQueryService requestQueryService, RequestPresentationService presentation) {
        this.requestQueryService = requestQueryService;
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

    @GetMapping("/requests/mine")
    public String myRequests(
            @AuthenticationPrincipal SmartFixUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) RequestStatus status,
            Model model) {
        var result =
                requestQueryService.listMyRequestsPage(principal.getUserId(), status, page, size);
        model.addAttribute("requests", result.getContent());
        model.addAttribute("pagination", result);
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("page", result.getNumber());

        return "request/mine";
    }

    @GetMapping("/requests/{ticketNumber}")
    public String requestDetails(
            @PathVariable String ticketNumber,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model) {
        MaintenanceRequestDetailsResponse request =
                requestQueryService.getRequestDetails(ticketNumber, principal.getUserId());

        model.addAttribute("request", request);
        model.addAttribute(
                "presentation", presentation.describe(ticketNumber, principal.getUserId()));

        return "request/detail";
    }

    @GetMapping("/admin/requests")
    public String queue(
            @AuthenticationPrincipal SmartFixUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) com.smartfix.request.domain.MaintenanceCategory category,
            @RequestParam(required = false) com.smartfix.request.domain.UrgencyLevel priority,
            @RequestParam(defaultValue = "newest") String sort,
            Model model) {
        model.addAttribute(
                "pagination",
                requestQueryService.searchForAdministration(principal.getUserId(), search, status, category, priority, sort, page, size));
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("search", search);
        model.addAttribute("categories", com.smartfix.request.domain.MaintenanceCategory.values());
        model.addAttribute("priorities", com.smartfix.request.domain.UrgencyLevel.values());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedPriority", priority);
        model.addAttribute("selectedSort", sort);
        return "admin/request-queue";
    }

    @GetMapping("/admin/requests/lookup")
    public String adminLookup(
            @RequestParam(required = false) String ticketNumber,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model) {
        if (ticketNumber != null && !ticketNumber.isBlank()) {
            MaintenanceRequestDetailsResponse request =
                    requestQueryService.getRequestDetails(
                            ticketNumber.trim(), principal.getUserId());

            model.addAttribute("request", request);
        }

        model.addAttribute("ticketNumber", ticketNumber);

        return "admin/requests";
    }
}
