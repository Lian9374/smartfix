package com.smartfix.reporting.controller;

import com.smartfix.reporting.dto.OperationalReportResponse;
import com.smartfix.reporting.service.OperationalReportService;
import com.smartfix.reporting.service.ReportingPeriodService;
import com.smartfix.reporting.service.ReportExportService;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.domain.UrgencyLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(
    controllers = ReportController.class,
    excludeAutoConfiguration =
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OperationalReportService reportService;

    @MockitoBean
    private ReportingPeriodService reportingPeriodService;

    @MockitoBean
    private ReportExportService reportExportService;

    @Test
    void reportUsesRequestedDateRangeAndShowsReport() throws Exception {
        Instant start =
            Instant.parse("2026-09-30T16:00:00Z");

        Instant end =
            Instant.parse("2026-10-31T16:00:00Z");

        var period =
            new ReportingPeriodService.ReportingPeriod(
                start,
                end);

        var report =
            new OperationalReportResponse(
                start,
                end,
                4,
                2,
                2,
                1,
                50.0,
                6.0,
                Map.of(RequestStatus.SUBMITTED, 1L),
                Map.of(MaintenanceCategory.ELECTRICAL, 2L),
                Map.of(UrgencyLevel.HIGH, 2L));

        when(reportingPeriodService.toPeriod(
            java.time.LocalDate.of(2026, 10, 1),
            java.time.LocalDate.of(2026, 10, 31)))
            .thenReturn(period);

        when(reportService.generate(start, end))
            .thenReturn(report);

        mvc.perform(get("/admin/reports")
                .param("start", "2026-10-01")
                .param("end", "2026-10-31"))
            .andExpect(status().isOk())
            .andExpect(view().name("reporting/report"))
            .andExpect(model().attribute("report", report))
            .andExpect(model().attribute(
                "startDate",
                java.time.LocalDate.of(2026, 10, 1)))
            .andExpect(model().attribute(
                "endDate",
                java.time.LocalDate.of(2026, 10, 31)));

        verify(reportingPeriodService)
            .toPeriod(
                java.time.LocalDate.of(2026, 10, 1),
                java.time.LocalDate.of(2026, 10, 31));

        verify(reportService)
            .generate(start, end);
    }

    @Test
    void exportCsvReturnsDownloadWithCsvBytes() throws Exception {
        Instant start =
            Instant.parse("2026-09-30T16:00:00Z");

        Instant end =
            Instant.parse("2026-10-31T16:00:00Z");

        var period =
            new ReportingPeriodService.ReportingPeriod(
                start,
                end);

        var report =
            new OperationalReportResponse(
                start,
                end,
                4,
                2,
                2,
                1,
                50.0,
                6.0,
                Map.of(RequestStatus.SUBMITTED, 1L),
                Map.of(MaintenanceCategory.ELECTRICAL, 2L),
                Map.of(UrgencyLevel.HIGH, 2L));

        byte[] csv =
            new byte[] {
                (byte) 0xEF,
                (byte) 0xBB,
                (byte) 0xBF,
                'A',
                'B',
                'C'
            };

        when(reportingPeriodService.toPeriod(
            java.time.LocalDate.of(2026, 10, 1),
            java.time.LocalDate.of(2026, 10, 31)))
            .thenReturn(period);

        when(reportService.generate(start, end))
            .thenReturn(report);

        when(reportExportService.exportCsv(report))
            .thenReturn(csv);

        mvc.perform(get("/admin/reports/export.csv")
                .param("start", "2026-10-01")
                .param("end", "2026-10-31"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                "Content-Disposition",
                "attachment; filename=\"smartfix-report.csv\""))
            .andExpect(content().contentTypeCompatibleWith(
                "text/csv"))
            .andExpect(content().bytes(csv));

        verify(reportingPeriodService)
            .toPeriod(
                java.time.LocalDate.of(2026, 10, 1),
                java.time.LocalDate.of(2026, 10, 31));

        verify(reportService)
            .generate(start, end);

        verify(reportExportService)
            .exportCsv(report);
    }
}
