package com.smartfix.notification.repository;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresNotificationInsertRepository
        implements NotificationInsertRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresNotificationInsertRepository(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean insertIfAbsent(
            Long recipientId,
            String eventType,
            String title,
            String message,
            Long referenceId,
            String dedupKey,
            Instant createdAt
    ) {
        boolean h2 = Boolean.TRUE.equals(jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                connection -> connection.getMetaData().getDatabaseProductName().equals("H2")));
        String sql = h2 ? """
                INSERT INTO notifications(recipient_id,event_type,dedup_key,title,message,reference_id,created_at)
                SELECT ?,?,?,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM notifications WHERE dedup_key=?)
                """ : """
                INSERT INTO notifications (
                    recipient_id,
                    event_type,
                    dedup_key,
                    title,
                    message,
                    reference_id,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (dedup_key) DO NOTHING
                """;

        if (h2) return jdbcTemplate.update(sql,recipientId,eventType,dedupKey,title,message,referenceId,
                java.sql.Timestamp.from(createdAt),dedupKey)==1;
        int rows = jdbcTemplate.update(
                sql,
                recipientId,
                eventType,
                dedupKey,
                title,
                message,
                referenceId,
                java.sql.Timestamp.from(createdAt)
        );

        return rows == 1;
    }
}
