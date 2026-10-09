package com.smartfix.request.dto;

import com.smartfix.request.domain.*;
import java.time.Instant;

/** Read-only execution context shared through the request service, never its repository. */
public record RequestWorkSummary(Long id, String ticketNumber, String title, Long locationId,
        UrgencyLevel priority, RequestStatus status, Instant createdAt) {}
