package com.smartfix.workorder.dto;

import com.smartfix.request.domain.*;
import com.smartfix.workorder.domain.WorkOrderStatus;
import java.time.Instant;

public record WorkOrderQueueRow(Long id, Long requestId, String ticketNumber, String title,
        Long locationId, UrgencyLevel priority, RequestStatus requestStatus, WorkOrderStatus status,
        Instant createdAt, Instant updatedAt) {}
