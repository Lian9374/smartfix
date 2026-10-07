package com.smartfix.workorder.dto;

import com.smartfix.workorder.domain.WorkOrderStatus;

import java.time.Instant;

public record WorkOrderResponse(
        Long id,
        Long requestId,
        String ticketNumber,
        Long technicianId,
        WorkOrderStatus status,
        String resolutionNote,
        Instant createdAt,
        Instant completedAt) {}
