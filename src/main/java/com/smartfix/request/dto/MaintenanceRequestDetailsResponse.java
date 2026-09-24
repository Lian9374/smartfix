package com.smartfix.request.dto;

import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.domain.UrgencyLevel;

import java.time.Instant;
import java.util.List;

public record MaintenanceRequestDetailsResponse(
    String ticketNumber,
    Long requesterId,
    Long locationId,
    String locationDisplayName,
    String title,
    String description,
    MaintenanceCategory category,
    UrgencyLevel urgencyLevel,
    RequestStatus status,
    Instant createdAt,
    Instant updatedAt,
    List<RequestStatusHistoryResponse> statusHistory
) {
}
