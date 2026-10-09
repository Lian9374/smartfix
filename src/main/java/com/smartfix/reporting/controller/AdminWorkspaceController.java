package com.smartfix.reporting.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.service.RequestQueryService;
import com.smartfix.user.domain.*;
import com.smartfix.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminWorkspaceController {
    private final RequestQueryService requests;
    private final UserService users;
    public AdminWorkspaceController(RequestQueryService requests, UserService users) {
        this.requests = requests; this.users = users;
    }
    @GetMapping("/admin")
    public String overview(@AuthenticationPrincipal SmartFixUserDetails principal, Model model) {
        var counts = requests.adminStatusCounts(principal.getUserId());
        model.addAttribute("totalRequests", counts.values().stream().mapToLong(Long::longValue).sum());
        model.addAttribute("reviewCount", counts.get(RequestStatus.SUBMITTED) + counts.get(RequestStatus.UNDER_REVIEW));
        model.addAttribute("repairCount", counts.get(RequestStatus.ASSIGNED) + counts.get(RequestStatus.IN_PROGRESS) + counts.get(RequestStatus.REOPENED));
        model.addAttribute("confirmationCount", counts.get(RequestStatus.CONFIRMED));
        model.addAttribute("technicianCount", users.listUsers().stream()
                .filter(u -> u.role() == Role.TECHNICIAN && u.accountStatus() == AccountStatus.ACTIVE).count());
        model.addAttribute("requests", requests.searchForAdministration(principal.getUserId(), null,
                RequestStatus.SUBMITTED, null, null, "oldest", 0, 5));
        return "admin/overview";
    }
}
