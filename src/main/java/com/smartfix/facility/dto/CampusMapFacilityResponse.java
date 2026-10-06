package com.smartfix.facility.dto;

import com.smartfix.facility.domain.FacilityStatus;

public record CampusMapFacilityResponse(
    Long id,
    String name,
    FacilityStatus status,
    Long locationId,
    String locationDisplayName,
    String building,
    String floor,
    String room) {
}
