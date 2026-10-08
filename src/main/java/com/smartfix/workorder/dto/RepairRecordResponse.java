package com.smartfix.workorder.dto;

import java.time.Instant;

public record RepairRecordResponse(
        Long id,
        Long technicianId,
        String diagnosis,
        String actionTaken,
        String materialsUsed,
        int minutesSpent,
        Long evidenceAttachmentId,
        Instant createdAt) {}
