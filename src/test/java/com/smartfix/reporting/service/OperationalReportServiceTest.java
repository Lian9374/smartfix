package com.smartfix.reporting.service;

import com.smartfix.reporting.dto.OperationalReportResponse;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.domain.UrgencyLevel;
import com.smartfix.request.dto.RequestSnapshotResponse;
import com.smartfix.request.service.RequestReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationalReportServiceTest {

    private RequestReadService requestReadService;
    private OperationalReportService reportService;

    @BeforeEach
    void setUp() {
        requestReadService = mock(RequestReadService.class);
        reportService =
            new OperationalReportService(requestReadService);
    }

    @Test
    void generatesMetricsFromKnownRequestDataset() {
        Instant start =
            Instant.parse("2026-10-01T00:00:00Z");

        Instant end =
            Instant.parse("2026-11-01T00:00:00Z");

        List<RequestSnapshotResponse> requests = List.of(
            request(
                1L,
                RequestStatus.SUBMITTED,
                MaintenanceCategory.ELECTRICAL,
                UrgencyLevel.HIGH,
                Instant.parse("2026-10-02T00:00:00Z"),
                null),

            request(
                2L,
                RequestStatus.RESOLVED,
                MaintenanceCategory.HVAC,
                UrgencyLevel.MEDIUM,
                Instant.parse("2026-10-03T00:00:00Z"),
                Instant.parse("2026-10-03T04:00:00Z")),

            request(
                3L,
                RequestStatus.CLOSED,
                MaintenanceCategory.ELECTRICAL,
                UrgencyLevel.HIGH,
                Instant.parse("2026-10-04T00:00:00Z"),
                Instant.parse("2026-10-04T08:00:00Z")),

            request(
                4L,
                RequestStatus.CANCELLED,
                MaintenanceCategory.PLUMBING,
                UrgencyLevel.LOW,
                Instant.parse("2026-10-05T00:00:00Z"),
                null));

        when(requestReadService.findCreatedBetween(
            start,
            end,
            0,
            100))
            .thenReturn(
                new PageImpl<>(
                    requests,
                    PageRequest.of(0, 100),
                    requests.size()));

        OperationalReportResponse report =
            reportService.generate(start, end);

        assertEquals(4, report.totalRequests());
        assertEquals(2, report.openRequests());
        assertEquals(2, report.resolvedRequests());
        assertEquals(1, report.closedRequests());

        assertEquals(
            50.0,
            report.resolutionRatePercent(),
            0.001);

        assertEquals(
            6.0,
            report.averageResolutionHours(),
            0.001);

        assertEquals(
            1L,
            report.requestsByStatus()
                .get(RequestStatus.SUBMITTED));

        assertEquals(
            2L,
            report.requestsByCategory()
                .get(MaintenanceCategory.ELECTRICAL));

        assertEquals(
            2L,
            report.requestsByUrgency()
                .get(UrgencyLevel.HIGH));

        verify(requestReadService)
            .findCreatedBetween(
                start,
                end,
                0,
                100);
    }

    private RequestSnapshotResponse request(
        Long id,
        RequestStatus status,
        MaintenanceCategory category,
        UrgencyLevel urgency,
        Instant createdAt,
        Instant resolvedAt) {

        return new RequestSnapshotResponse(
            id,
            "SF-2026-" + String.format("%06d", id),
            100L + id,
            200L + id,
            category,
            urgency,
            status,
            createdAt,
            resolvedAt,
            status == RequestStatus.CLOSED
                ? resolvedAt
                : null);
    }
}
