package com.smartfix.cloud;

public record NotificationEnvelope(int schemaVersion, String eventType, long taskId, String dedupKey,
        long recipientId, String channel, String notificationType, String title, String message,
        Long referenceId, String email, String occurredAt) {}
