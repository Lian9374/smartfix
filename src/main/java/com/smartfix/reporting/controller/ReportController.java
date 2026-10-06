package com.smartfix.reporting.controller;

import com.smartfix.reporting.service.OperationalReportService;
import com.smartfix.reporting.service.ReportingPeriodService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.smartfix.reporting.service.ReportExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.ZoneId;

@Controller
@RequestMapping("/admin/reports")
public class ReportController {

    private static final ZoneId REPORTING_ZONE =
        ZoneId.of("Asia/Singapore");

    private final OperationalReportService reportService;
    private final ReportingPeriodService reportingPeriodService;
    private final ReportExportService reportExportService;

    public ReportController(
        OperationalReportService reportService,
        ReportingPeriodService reportingPeriodService,
        ReportExportService reportExportService) {

        this.reportService = reportService;
        this.reportingPeriodService = reportingPeriodService;
        this.reportExportService = reportExportService;
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

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate start,

        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate end) {

        var period =
            reportingPeriodService.toPeriod(
                start,
                end);

        var report =
            reportService.generate(
                period.start(),
                period.endExclusive());

        byte[] csv =
            reportExportService.exportCsv(report);

        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"smartfix-report.csv\"")
            .contentType(
                new MediaType(
                    "text",
                    "csv",
                    StandardCharsets.UTF_8))
            .body(csv);
    }
}
