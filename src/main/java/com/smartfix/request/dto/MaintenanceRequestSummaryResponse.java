package com.smartfix.request.dto;

import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.domain.UrgencyLevel;

import java.time.Instant;

public record MaintenanceRequestSummaryResponse(
    String ticketNumber,
    String title,
    MaintenanceCategory category,
    UrgencyLevel urgencyLevel,
    RequestStatus status,
    String locationDisplayName,
    Instant createdAt
) {
}
