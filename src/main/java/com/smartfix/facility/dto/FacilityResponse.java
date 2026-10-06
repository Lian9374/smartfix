package com.smartfix.facility.dto;

import com.smartfix.facility.domain.FacilityStatus;

import java.time.OffsetDateTime;

public record FacilityResponse(
    Long id,
    Long locationId,
    String locationDisplayName,
    String name,
    String description,
    FacilityStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
}
