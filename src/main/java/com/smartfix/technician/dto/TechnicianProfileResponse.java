package com.smartfix.technician.dto;

import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.technician.domain.AvailabilityStatus;

import java.time.Instant;
import java.util.Set;

public record TechnicianProfileResponse(Long profileId, Long userId,
        Set<MaintenanceCategory> skills, Set<Long> serviceAreaIds,
        AvailabilityStatus availabilityStatus, boolean active, long version, Instant updatedAt) {
    public TechnicianProfileResponse {
        skills = Set.copyOf(skills);
        serviceAreaIds = Set.copyOf(serviceAreaIds);
    }
}
