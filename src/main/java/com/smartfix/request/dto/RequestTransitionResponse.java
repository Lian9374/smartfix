package com.smartfix.request.dto;

import com.smartfix.request.domain.RequestStatus;

import java.time.Instant;

public record RequestTransitionResponse(
        Long requestId,
        String ticketNumber,
        RequestStatus fromStatus,
        RequestStatus toStatus,
        Long actorUserId,
        Instant occurredAt) {}
