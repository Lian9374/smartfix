package com.smartfix.reporting.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.domain.RequestStage;
import com.smartfix.request.service.RequestQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminWorkspaceController {
    private final RequestQueryService requests;
    public AdminWorkspaceController(RequestQueryService requests) {
        this.requests = requests;
    }
    @GetMapping("/admin")
    public String overview(@AuthenticationPrincipal SmartFixUserDetails principal, Model model) {
        var counts = requests.adminStatusCounts(principal.getUserId());
        model.addAttribute("totalRequests", counts.values().stream().mapToLong(Long::longValue).sum());
        var stages = new java.util.LinkedHashMap<String, Long>();
        for (var stage : RequestStage.values()) {
            stages.put(stage.name(), stage.getStatuses().stream().mapToLong(counts::get).sum());
        }
        model.addAttribute("stages", RequestStage.values());
        model.addAttribute("stageCounts", stages);
        model.addAttribute("requests", requests.searchForAdministration(principal.getUserId(), null,
                null, RequestStage.TO_ASSIGN, null, null, "oldest", 0, 3));
        model.addAttribute("finalReviews", requests.searchForAdministration(principal.getUserId(), null,
                null, RequestStage.ADMIN_REVIEW, null, null, "oldest", 0, 3));
        return "admin/overview";
    }
}
