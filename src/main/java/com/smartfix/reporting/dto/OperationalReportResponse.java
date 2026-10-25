package com.smartfix.reporting.dto;

import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.domain.UrgencyLevel;

import java.time.Instant;
import java.util.Map;

public record OperationalReportResponse(
    Instant start,
    Instant end,
    long totalRequests,
    long openRequests,
    long resolvedRequests,
    long closedRequests,
    double resolutionRatePercent,
    double averageResolutionHours,
    Map<RequestStatus, Long> requestsByStatus,
    Map<MaintenanceCategory, Long> requestsByCategory,
    Map<UrgencyLevel, Long> requestsByUrgency) {
    // Own the collection structure; preserve existing null/element semantics.
    public OperationalReportResponse {
        requestsByStatus = requestsByStatus == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(requestsByStatus));
        requestsByCategory = requestsByCategory == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(requestsByCategory));
        requestsByUrgency = requestsByUrgency == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(requestsByUrgency));
    }

}
