package com.smartfix.request.dto;

import com.smartfix.request.domain.*;
import java.time.Instant;

public record AdminRequestRow(Long id, String ticketNumber, String title,
        MaintenanceCategory category, UrgencyLevel priority, RequestStatus status,
        String locationDisplayName, Instant createdAt) {}
