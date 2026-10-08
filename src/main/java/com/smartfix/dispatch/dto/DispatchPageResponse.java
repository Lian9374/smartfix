package com.smartfix.dispatch.dto;

import com.smartfix.request.dto.MaintenanceRequestDetailsResponse;
import com.smartfix.request.domain.UrgencyLevel;

import java.util.List;

/** Read model assembled only through the other modules' public services. */
public record DispatchPageResponse(MaintenanceRequestDetailsResponse request, UrgencyLevel priority,
        AssignmentResponse currentAssignment, String currentTechnicianName,
        List<Candidate> candidates, boolean canAssign, boolean canReassign, boolean canWithdraw,
        String recommendationNotice) {
    public record Candidate(Long userId, String displayName, String skills, String serviceAreas,
            String availability, long openWorkOrders, boolean selectable) { }
}
