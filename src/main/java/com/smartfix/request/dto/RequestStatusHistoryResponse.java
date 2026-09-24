package com.smartfix.request.dto;

import com.smartfix.request.domain.RequestStatus;

import java.time.Instant;

public record RequestStatusHistoryResponse(
    RequestStatus fromStatus,
    RequestStatus toStatus,
    Long changedByUserId,
    Instant changedAt,
    String comment
) {
}
