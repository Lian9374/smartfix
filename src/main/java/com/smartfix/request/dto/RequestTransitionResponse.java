package com.smartfix.request.dto;

import com.smartfix.request.domain.RequestStatus;

import java.time.Instant;

public record RequestTransitionResponse(
        Long requestId,
        String ticketNumber,
        RequestStatus fromStatus,
        RequestStatus toStatus,
        Long actorUserId,
        Instant occurredAt,
        Long historyId) {
    public RequestTransitionResponse(Long requestId, String ticketNumber, RequestStatus fromStatus,
            RequestStatus toStatus, Long actorUserId, Instant occurredAt) {
        this(requestId, ticketNumber, fromStatus, toStatus, actorUserId, occurredAt, null);
    }
}
