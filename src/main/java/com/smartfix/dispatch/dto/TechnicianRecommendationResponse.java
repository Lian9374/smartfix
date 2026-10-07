package com.smartfix.dispatch.dto;

import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.technician.domain.AvailabilityStatus;

import java.util.Set;

/** One ranked candidate. userId identifies the account; profileId is the stable ranking tie-breaker. */
public record TechnicianRecommendationResponse(Long profileId, Long userId, String displayName,
        Set<MaintenanceCategory> skills, Set<Long> serviceAreaIds,
        AvailabilityStatus availabilityStatus, long openWorkOrders) {
    public TechnicianRecommendationResponse {
        skills = Set.copyOf(skills);
        serviceAreaIds = Set.copyOf(serviceAreaIds);
        if (openWorkOrders < 0) {
            throw new IllegalArgumentException("Open work order count cannot be negative.");
        }
    }
}
