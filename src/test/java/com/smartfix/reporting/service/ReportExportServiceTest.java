package com.smartfix.reporting.service;

import com.smartfix.reporting.dto.OperationalReportResponse;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.domain.UrgencyLevel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportExportServiceTest {

    private final ReportExportService service =
        new ReportExportService();

    @Test
    void exportHasUtf8BomAndFixedColumnOrder() {
        Map<RequestStatus, Long> byStatus =
            new LinkedHashMap<>();
        byStatus.put(RequestStatus.SUBMITTED, 2L);
        byStatus.put(RequestStatus.CLOSED, 1L);

        Map<MaintenanceCategory, Long> byCategory =
            new LinkedHashMap<>();
        byCategory.put(MaintenanceCategory.ELECTRICAL, 2L);

        Map<UrgencyLevel, Long> byUrgency =
            new LinkedHashMap<>();
        byUrgency.put(UrgencyLevel.HIGH, 2L);

        var report =
            new OperationalReportResponse(
                Instant.parse("2026-09-30T16:00:00Z"),
                Instant.parse("2026-10-31T16:00:00Z"),
                4,
                2,
                2,
                1,
                50.0,
                6.0,
                byStatus,
                byCategory,
                byUrgency);

        byte[] csv = service.exportCsv(report);

        assertEquals((byte) 0xEF, csv[0]);
        assertEquals((byte) 0xBB, csv[1]);
        assertEquals((byte) 0xBF, csv[2]);

        String text =
            new String(
                csv,
                3,
                csv.length - 3,
                StandardCharsets.UTF_8);

        assertTrue(text.startsWith(
            "Start,End,Total Requests,Open Requests,"
                + "Resolved Requests,Closed Requests,"
                + "Resolution Rate (%),"
                + "Average Resolution Hours\r\n"));

        assertTrue(text.contains(
            "2026-09-30T16:00:00Z,"
                + "2026-10-31T16:00:00Z,"
                + "4,2,2,1,50.00,6.00"));

        assertTrue(text.contains(
            "Status,Requests\r\n"));

        assertTrue(text.contains(
            "Category,Requests\r\n"));

        assertTrue(text.contains(
            "Urgency,Requests\r\n"));
    }
}
