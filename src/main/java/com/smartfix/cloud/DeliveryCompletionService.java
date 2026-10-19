package com.smartfix.cloud;

import com.smartfix.notification.repository.NotificationInsertRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.sql.Timestamp;

/** Core owns this read model; Worker never obtains Core database access. */
@Service
@Profile("distributed")
public class DeliveryCompletionService {
    private final JdbcTemplate jdbc;
    private final NotificationInsertRepository notifications;
    private final TransactionTemplate transactions;
    private final Clock clock;
    public DeliveryCompletionService(JdbcTemplate jdbc, NotificationInsertRepository notifications,
                                    TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc; this.notifications = notifications; this.transactions = transactions; this.clock = clock;
    }
    public void complete(long id, String key, String outcome) {
        transactions.executeWithoutResult(tx -> {
            var rows = jdbc.queryForList("SELECT * FROM delivery_tasks WHERE id=? FOR UPDATE", id);
            if (rows.isEmpty()) throw new IllegalArgumentException("Unknown delivery");
            var task = rows.getFirst();
            if (!task.get("dedup_key").equals(key)) throw new IllegalArgumentException("Delivery identity mismatch");
            boolean inApp = "IN_APP".equals(task.get("channel"));
            if (!"DELIVERED".equals(outcome) && !("SKIPPED".equals(outcome) && !inApp))
                throw new IllegalArgumentException("Invalid delivery outcome");
            if (isTerminal(task.get("status"))) return;
            if (inApp) notifications.insertIfAbsent(((Number)task.get("recipient_id")).longValue(),
                    (String)task.get("event_type"), (String)task.get("title"), (String)task.get("message"),
                    task.get("reference_id") == null ? null : ((Number)task.get("reference_id")).longValue(), key, clock.instant());
            jdbc.update("UPDATE delivery_tasks SET status=?,delivered_at=?,last_error=NULL WHERE id=?",
                    outcome, Timestamp.from(clock.instant()), id);
        });
    }

    private static boolean isTerminal(Object status) {
        return "DELIVERED".equals(status) || "SKIPPED".equals(status);
    }
}
