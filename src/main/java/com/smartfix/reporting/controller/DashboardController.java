package com.smartfix.reporting.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.reporting.service.DashboardService;
import com.smartfix.reporting.service.ReportingPeriodService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Controller
public class DashboardController {

    private static final ZoneId REPORTING_ZONE =
        ZoneId.of("Asia/Singapore");

    private final DashboardService dashboardService;
    private final ReportingPeriodService reportingPeriodService;
    private final Clock clock;

    public DashboardController(
        DashboardService dashboardService,
        ReportingPeriodService reportingPeriodService,
        Clock clock) {

        this.dashboardService = dashboardService;
        this.reportingPeriodService = reportingPeriodService;
        this.clock = clock;
    }

    @GetMapping("/dashboard")
    public String adminDashboard(Model model) {

        LocalDate today = LocalDate.now(
            clock.withZone(REPORTING_ZONE));

        LocalDate startDate = today.withDayOfMonth(1);

        var period = reportingPeriodService.toPeriod(
            startDate,
            today);

        var dashboard = dashboardService.getAdminDashboard(
            period.start(),
            period.endExclusive());

        model.addAttribute("dashboard", dashboard);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", today);

        return "reporting/dashboard";
    }

    @GetMapping("/technician/dashboard")
    public String technicianDashboard(
        @AuthenticationPrincipal SmartFixUserDetails principal,
        Model model) {

        var dashboard = dashboardService.getTechnicianDashboard(
            principal.getUserId());

        model.addAttribute("dashboard", dashboard);

        return "reporting/technician-dashboard";
    }
}
