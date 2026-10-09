package com.smartfix.dispatch.event;

import java.time.Instant;

/** Published inside the assignment transaction; notification/audit consumers must use AFTER_COMMIT. */
public record AssignmentCreatedEvent(Long assignmentId, Long requestId, String ticketNumber,
        Long requesterId, Long technicianId, Long previousTechnicianId, Long actorUserId,
        String reason, Instant occurredAt) { }
