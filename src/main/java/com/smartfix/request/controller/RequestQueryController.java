package com.smartfix.request.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.dto.MaintenanceRequestDetailsResponse;
import com.smartfix.request.service.RequestQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RequestQueryController {

    private final RequestQueryService requestQueryService;

    public RequestQueryController(RequestQueryService requestQueryService) {
        this.requestQueryService = requestQueryService;
    }

    @GetMapping("/requests/mine")
    public String myRequests(
        @AuthenticationPrincipal SmartFixUserDetails principal,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        Model model
    ) {
        model.addAttribute(
            "requests",
            requestQueryService.listMyRequests(
                principal.getUserId(),
                page,
                size
            )
        );

        model.addAttribute("page", Math.max(page, 0));

        return "request/mine";
    }

    @GetMapping("/requests/{ticketNumber}")
    public String requestDetails(
        @PathVariable String ticketNumber,
        @AuthenticationPrincipal SmartFixUserDetails principal,
        Model model
    ) {
        MaintenanceRequestDetailsResponse request =
            requestQueryService.getRequestDetails(
                ticketNumber,
                principal.getUserId()
            );

        model.addAttribute("request", request);

        return "request/detail";
    }

    @GetMapping("/admin/requests/lookup")
    public String adminLookup(
        @RequestParam(required = false) String ticketNumber,
        @AuthenticationPrincipal SmartFixUserDetails principal,
        Model model
    ) {
        if (ticketNumber != null && !ticketNumber.isBlank()) {
            MaintenanceRequestDetailsResponse request =
                requestQueryService.getRequestDetails(
                    ticketNumber.trim(),
                    principal.getUserId()
                );

            model.addAttribute("request", request);
        }

        model.addAttribute("ticketNumber", ticketNumber);

        return "admin/requests";
    }
}
