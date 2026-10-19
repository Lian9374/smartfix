package com.smartfix.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfix.user.service.NotificationContactService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import java.sql.Timestamp;
import java.time.Clock;

/** Send before commit permits duplicates, but never loses an already committed delivery intent. */
@Service
@Profile("distributed")
public class OutboxRelay {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final SqsClient sqs;
    private final ObjectMapper json;
    private final NotificationContactService contacts;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final String queueUrl;
    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate tx, SqsClient sqs, ObjectMapper json,
                       NotificationContactService contacts, Clock clock, MeterRegistry metrics,
                       @Value("${smartfix.cloud.queue-url}") String queueUrl) {
        this.jdbc=jdbc; this.tx=tx; this.sqs=sqs; this.json=json; this.contacts=contacts;
        this.clock=clock; this.metrics=metrics; this.queueUrl=queueUrl;
        metrics.gauge("smartfix.outbox.pending", this, r -> r.pending());
    }
    private static String instant(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant().toString();
        return ((java.time.OffsetDateTime)value).toInstant().toString();
    }
    public double pending() { return jdbc.queryForObject("SELECT count(*) FROM delivery_tasks WHERE status='PENDING'", Long.class); }
    @Scheduled(fixedDelayString="${smartfix.cloud.relay-milliseconds:1000}")
    public void publish() {
        try {
            tx.executeWithoutResult(status -> {
                var rows=jdbc.queryForList("SELECT * FROM delivery_tasks WHERE status='PENDING' AND next_attempt_at<=? ORDER BY id LIMIT 10 FOR UPDATE SKIP LOCKED", Timestamp.from(clock.instant()));
                for (var row : rows) {
                    long id=((Number)row.get("id")).longValue();
                    try {
                        long recipient=((Number)row.get("recipient_id")).longValue();
                        String channel=(String)row.get("channel");
                        var event=new NotificationEnvelope(1,"notification.requested.v1",id,(String)row.get("dedup_key"),recipient,
                                channel,(String)row.get("event_type"),(String)row.get("title"),(String)row.get("message"),
                                row.get("reference_id")==null?null:((Number)row.get("reference_id")).longValue(),
                                "EMAIL".equals(channel)?contacts.email(recipient).orElse(null):null,
                                instant(row.get("created_at")));
                        String body=json.writeValueAsString(event);
                        sqs.sendMessage(r -> r.queueUrl(queueUrl).messageBody(body));
                        jdbc.update("UPDATE delivery_tasks SET status='QUEUED',last_error=NULL WHERE id=?",id);
                        metrics.counter("smartfix.outbox.published").increment();
                    } catch (Exception failure) {
                        int attempts=((Number)row.get("attempts")).intValue()+1;
                        long delay=Math.min(300,1L<<Math.min(attempts,8));
                        jdbc.update("UPDATE delivery_tasks SET attempts=?,last_error=?,next_attempt_at=? WHERE id=?",
                                attempts,failure.getClass().getSimpleName(),Timestamp.from(clock.instant().plusSeconds(delay)),id);
                        metrics.counter("smartfix.outbox.failures").increment();
                        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Outbox publish failed taskId={}, failureType={}",id,failure.getClass().getSimpleName());
                    }
                }
            });
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Outbox transaction failed: {}",failure.getClass().getSimpleName());
        }
    }
}
