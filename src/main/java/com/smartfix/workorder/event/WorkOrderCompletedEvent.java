package com.smartfix.workorder.event;

import java.time.Instant;

public record WorkOrderCompletedEvent(
        Long workOrderId,
        Long requestId,
        String ticketNumber,
        Long technicianId,
        Long requesterId,
        Instant occurredAt,
        Long resolutionHistoryId) {
    public WorkOrderCompletedEvent(Long workOrderId, Long requestId, String ticketNumber,
            Long technicianId, Long requesterId, Instant occurredAt) {
        this(workOrderId, requestId, ticketNumber, technicianId, requesterId, occurredAt, null);
    }
}
