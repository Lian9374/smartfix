package com.smartfix.reporting.service;

import com.smartfix.reporting.dto.AdminDashboardResponse;
import com.smartfix.reporting.dto.TechnicianDashboardResponse;
import com.smartfix.reporting.dto.OperationalReportResponse;
import com.smartfix.technician.service.TechnicianWorkloadService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final OperationalReportService reportService;
    private final TechnicianWorkloadService workloadService;

    public DashboardService(
        OperationalReportService reportService,
        TechnicianWorkloadService workloadService) {

        this.reportService = reportService;
        this.workloadService = workloadService;
    }

    public AdminDashboardResponse getAdminDashboard(
        Instant start,
        Instant end) {

        OperationalReportResponse report =
            reportService.generate(start, end);

        return new AdminDashboardResponse(
            report.totalRequests(),
            report.openRequests(),
            report.resolvedRequests(),
            report.closedRequests(),
            report.resolutionRatePercent(),
            report.averageResolutionHours());
    }

    public TechnicianDashboardResponse getTechnicianDashboard(
        Long technicianUserId) {

        long openWorkOrders =
            workloadService.countOpenWorkOrders(
                technicianUserId);

        return new TechnicianDashboardResponse(
            technicianUserId,
            openWorkOrders);
    }
}
