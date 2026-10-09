package com.smartfix.dispatch.dto;

import com.smartfix.dispatch.domain.Assignment;
import java.time.Instant;

/** Internal immutable read model; controllers must enforce role/resource access before exposing it. */
public record AssignmentResponse(Long id, Long requestId, Long technicianId, Long assignedByUserId,
        Instant assignedAt, String reason, boolean active, Long deactivatedByUserId,
        Instant deactivatedAt, String deactivationReason) {
    public static AssignmentResponse from(Assignment assignment) {
        return new AssignmentResponse(assignment.getId(), assignment.getRequestId(), assignment.getTechnicianId(),
                assignment.getAssignedByUserId(), assignment.getAssignedAt(), assignment.getReason(),
                assignment.isActive(), assignment.getDeactivatedByUserId(), assignment.getDeactivatedAt(),
                assignment.getDeactivationReason());
    }
}
