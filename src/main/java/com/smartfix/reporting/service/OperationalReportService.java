package com.smartfix.reporting.service;

import com.smartfix.common.exception.InputValidationException;
import com.smartfix.reporting.dto.OperationalReportResponse;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.domain.UrgencyLevel;
import com.smartfix.request.dto.RequestSnapshotResponse;
import com.smartfix.request.service.RequestReadService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;

@Service
@Transactional(readOnly = true)
public class OperationalReportService {
    private static final int PAGE_SIZE = 100;
    private final RequestReadService requestReadService;
    public OperationalReportService(RequestReadService requestReadService) {
        this.requestReadService = requestReadService;
    }
    public OperationalReportResponse generate(Instant start, Instant end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new InputValidationException("A valid reporting interval is required.");
        }
        long total = 0, open = 0, resolved = 0, closed = 0, timed = 0;
        double resolutionSeconds = 0;
        var statuses = new EnumMap<RequestStatus, Long>(RequestStatus.class);
        var categories = new EnumMap<MaintenanceCategory, Long>(MaintenanceCategory.class);
        var urgencies = new EnumMap<UrgencyLevel, Long>(UrgencyLevel.class);
        int pageNumber = 0;
        Page<RequestSnapshotResponse> page;
        do {
            page = requestReadService.findCreatedBetween(start, end, pageNumber++, PAGE_SIZE);
            // Retain one DTO page instead of all requests; scan each record once.
            for (var request : page) {
                total++;
                switch (request.status()) {
                    case CLOSED, REJECTED, CANCELLED -> { }
                    default -> open++;
                }
                switch (request.status()) {
                    case RESOLVED, CONFIRMED, CLOSED -> resolved++;
                    default -> { }
                }
                if (request.status() == RequestStatus.CLOSED) closed++;
                if (request.resolvedAt() != null) {
                    resolutionSeconds += Duration.between(request.createdAt(), request.resolvedAt()).getSeconds();
                    timed++;
                }
                statuses.merge(request.status(), 1L, Long::sum);
                categories.merge(request.category(), 1L, Long::sum);
                urgencies.merge(request.effectiveUrgencyLevel(), 1L, Long::sum);
            }
        } while (page.hasNext());
        return new OperationalReportResponse(start, end, total, open, resolved, closed,
                total == 0 ? 0.0 : resolved * 100.0 / total,
                timed == 0 ? 0.0 : resolutionSeconds / timed / 3600.0,
                statuses, categories, urgencies);
    }
}
