package com.smartfix.request.event;

import com.smartfix.request.domain.RequestStatus;

import java.time.Instant;

/**
 * Publish inside the business transaction; E consumes with AFTER_COMMIT and its own retry policy.
 */
public record RequestStatusChangedEvent(
        Long requestId,
        String ticketNumber,
        Long requesterId,
        RequestStatus fromStatus,
        RequestStatus toStatus,
        Long actorUserId,
        Instant occurredAt) {}
