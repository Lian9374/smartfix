package com.smartfix.dispatch.event;

import java.time.Instant;

/** An explicit withdrawal, not the intermediate step of a reassignment. Consume AFTER_COMMIT. */
public record AssignmentWithdrawnEvent(Long assignmentId, Long requestId, String ticketNumber,
        Long requesterId, Long technicianId, Long actorUserId, String reason, Instant occurredAt) { }
