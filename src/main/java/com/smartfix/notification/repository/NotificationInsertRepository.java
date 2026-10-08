package com.smartfix.notification.repository;

import java.time.Instant;

public interface NotificationInsertRepository {

    boolean insertIfAbsent(
            Long recipientId,
            String eventType,
            String title,
            String message,
            Long referenceId,
            String dedupKey,
            Instant createdAt
    );
}