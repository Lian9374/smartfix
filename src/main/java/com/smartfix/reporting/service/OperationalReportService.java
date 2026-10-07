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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class OperationalReportService {

    private static final int PAGE_SIZE = 100;

    private final RequestReadService requestReadService;

    public OperationalReportService(RequestReadService requestReadService) {
        this.requestReadService = requestReadService;
    }

    public OperationalReportResponse generate(
        Instant start,
        Instant end) {

        if (start == null || end == null || !start.isBefore(end)) {
            throw new InputValidationException(
                "A valid reporting interval is required.");
        }

        List<RequestSnapshotResponse> requests =
            loadRequests(start, end);

        long total = requests.size();

        long open = requests.stream()
            .filter(this::isOpen)
            .count();

        long resolved = requests.stream()
            .filter(this::isResolved)
            .count();

        long closed = requests.stream()
            .filter(request ->
                request.status() == RequestStatus.CLOSED)
            .count();

        double resolutionRate =
            total == 0
                ? 0.0
                : resolved * 100.0 / total;

        double averageResolutionHours =
            calculateAverageResolutionHours(requests);

        return new OperationalReportResponse(
            start,
            end,
            total,
            open,
            resolved,
            closed,
            resolutionRate,
            averageResolutionHours,
            countByStatus(requests),
            countByCategory(requests),
            countByUrgency(requests));
    }

    private List<RequestSnapshotResponse> loadRequests(
        Instant start,
        Instant end) {

        List<RequestSnapshotResponse> result =
            new ArrayList<>();

        int pageNumber = 0;
        Page<RequestSnapshotResponse> page;

        do {
            page = requestReadService.findCreatedBetween(
                start,
                end,
                pageNumber,
                PAGE_SIZE);

            result.addAll(page.getContent());
            pageNumber++;
        } while (page.hasNext());

        return result;
    }

    private boolean isOpen(RequestSnapshotResponse request) {
        return switch (request.status()) {
            case CLOSED, REJECTED, CANCELLED -> false;
            default -> true;
        };
    }

    private boolean isResolved(RequestSnapshotResponse request) {
        return switch (request.status()) {
            case RESOLVED, CONFIRMED, CLOSED -> true;
            default -> false;
        };
    }

    private double calculateAverageResolutionHours(
        List<RequestSnapshotResponse> requests) {

        List<Long> resolutionSeconds = requests.stream()
            .filter(request -> request.resolvedAt() != null)
            .map(request ->
                Duration.between(
                        request.createdAt(),
                        request.resolvedAt())
                    .getSeconds())
            .toList();

        if (resolutionSeconds.isEmpty()) {
            return 0.0;
        }

        double averageSeconds =
            resolutionSeconds.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        return averageSeconds / 3600.0;
    }

    private Map<RequestStatus, Long> countByStatus(
        List<RequestSnapshotResponse> requests) {

        Map<RequestStatus, Long> counts =
            new EnumMap<>(RequestStatus.class);

        for (RequestSnapshotResponse request : requests) {
            counts.merge(
                request.status(),
                1L,
                Long::sum);
        }

        return counts;
    }

    private Map<MaintenanceCategory, Long> countByCategory(
        List<RequestSnapshotResponse> requests) {

        Map<MaintenanceCategory, Long> counts =
            new EnumMap<>(MaintenanceCategory.class);

        for (RequestSnapshotResponse request : requests) {
            counts.merge(
                request.category(),
                1L,
                Long::sum);
        }

        return counts;
    }

    private Map<UrgencyLevel, Long> countByUrgency(
        List<RequestSnapshotResponse> requests) {

        Map<UrgencyLevel, Long> counts =
            new EnumMap<>(UrgencyLevel.class);

        for (RequestSnapshotResponse request : requests) {
            counts.merge(
                request.effectiveUrgencyLevel(),
                1L,
                Long::sum);
        }

        return counts;
    }
}
