package com.smartfix.reporting.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.reporting.dto.OperationalReportResponse;
import com.smartfix.reporting.dto.AdminDashboardResponse;
import com.smartfix.reporting.dto.TechnicianDashboardResponse;
import com.smartfix.technician.service.TechnicianWorkloadService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {

    private OperationalReportService reportService;
    private TechnicianWorkloadService workloadService;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        reportService = mock(OperationalReportService.class);
        workloadService = mock(TechnicianWorkloadService.class);

        dashboardService = new DashboardService(
            reportService,
            workloadService);
    }

    @Test
    void adminDashboardUsesRealReportMetrics() {
        Instant start =
            Instant.parse("2026-10-01T00:00:00Z");

        Instant end =
            Instant.parse("2026-11-01T00:00:00Z");

        OperationalReportResponse report =
            new OperationalReportResponse(
                start,
                end,
                10,
                6,
                4,
                2,
                40.0,
                8.5,
                Map.of(),
                Map.of(),
                Map.of());

        when(reportService.generate(start, end))
            .thenReturn(report);

        AdminDashboardResponse result =
            dashboardService.getAdminDashboard(
                start,
                end);

        assertEquals(10, result.totalRequests());
        assertEquals(6, result.openRequests());
        assertEquals(4, result.resolvedRequests());
        assertEquals(2, result.closedRequests());
        assertEquals(40.0, result.resolutionRatePercent());
        assertEquals(8.5, result.averageResolutionHours());

        verify(reportService).generate(start, end);
    }

    @Test
    void technicianDashboardShowsOwnWorkload() {
        Long technicianId = 7L;

        when(workloadService.countOpenWorkOrders(technicianId))
            .thenReturn(3L);

        TechnicianDashboardResponse result =
            dashboardService.getTechnicianDashboard(
                technicianId);

        assertEquals(technicianId, result.technicianUserId());
        assertEquals(3L, result.openWorkOrders());

        verify(workloadService)
            .countOpenWorkOrders(technicianId);
    }

    @Test
    void unavailableWorkloadIsNotReportedAsZero() {
        Long technicianId = 7L;

        when(workloadService.countOpenWorkOrders(technicianId))
            .thenThrow(new BusinessConflictException(
                "Technician workload is unavailable."));

        assertThrows(
            BusinessConflictException.class,
            () -> dashboardService.getTechnicianDashboard(
                technicianId));
    }
}
