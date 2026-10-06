package com.smartfix.reporting.controller;

import com.smartfix.reporting.service.OperationalReportService;
import com.smartfix.reporting.service.ReportingPeriodService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.ZoneId;

@Controller
@RequestMapping("/admin/reports")
public class ReportController {

    private static final ZoneId REPORTING_ZONE =
        ZoneId.of("Asia/Singapore");

    private final OperationalReportService reportService;
    private final ReportingPeriodService reportingPeriodService;

    public ReportController(
        OperationalReportService reportService,
        ReportingPeriodService reportingPeriodService) {

        this.reportService = reportService;
        this.reportingPeriodService = reportingPeriodService;
    }

    @GetMapping
    public String report(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate start,

        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate end,

        Model model) {

        LocalDate today = LocalDate.now(REPORTING_ZONE);

        LocalDate effectiveStart =
            start != null
                ? start
                : today.withDayOfMonth(1);

        LocalDate effectiveEnd =
            end != null
                ? end
                : today;

        var period = reportingPeriodService.toPeriod(
            effectiveStart,
            effectiveEnd);

        var report = reportService.generate(
            period.start(),
            period.endExclusive());

        model.addAttribute("report", report);
        model.addAttribute("startDate", effectiveStart);
        model.addAttribute("endDate", effectiveEnd);

        return "reporting/report";
    }
}
