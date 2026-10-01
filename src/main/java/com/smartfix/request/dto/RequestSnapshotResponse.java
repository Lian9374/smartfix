package com.smartfix.request.dto;

import com.smartfix.request.domain.*;

import java.time.Instant;

/** Internal public read API for B/D/E; controllers must still authorize browser access. */
public record RequestSnapshotResponse(
        Long id,
        String ticketNumber,
        Long requesterId,
        Long locationId,
        MaintenanceCategory category,
        UrgencyLevel effectiveUrgencyLevel,
        RequestStatus status,
        Instant createdAt,
        Instant resolvedAt,
        Instant closedAt) {}
