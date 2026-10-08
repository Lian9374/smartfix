package com.smartfix.audit.dto;

import java.time.Instant;

public record AuditEntryResponse(Long id, Long actorUserId, String action, String targetType,
                                 Long targetId, String outcome, Instant occurredAt) {}
