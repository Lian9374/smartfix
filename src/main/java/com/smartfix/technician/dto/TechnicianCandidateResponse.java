package com.smartfix.technician.dto;

import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.technician.domain.AvailabilityStatus;

import java.util.Set;

/** Eligible directory entry. Workload and recommendation ranking belong to S3-B-02. */
public record TechnicianCandidateResponse(Long profileId, Long userId, String displayName,
        Set<MaintenanceCategory> skills, Set<Long> serviceAreaIds, AvailabilityStatus availabilityStatus) {
    public TechnicianCandidateResponse {
        skills = Set.copyOf(skills);
        serviceAreaIds = Set.copyOf(serviceAreaIds);
    }
}
