package com.smartfix.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_entries")
public class AuditEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, name = "actor_user_id") private Long actorUserId;
    @Column(nullable = false, length = 80) private String action;
    @Column(nullable = false, name = "target_type", length = 30) private String targetType;
    @Column(nullable = false, name = "target_id") private Long targetId;
    @Column(nullable = false, length = 30) private String outcome;
    @Column(nullable = false, name = "occurred_at") private Instant occurredAt;

    protected AuditEntry() {}
    public AuditEntry(Long actorUserId, String action, String targetType, Long targetId,
                      String outcome, Instant occurredAt) {
        this.actorUserId = java.util.Objects.requireNonNull(actorUserId);
        this.action = java.util.Objects.requireNonNull(action);
        this.targetType = java.util.Objects.requireNonNull(targetType);
        this.targetId = java.util.Objects.requireNonNull(targetId);
        this.outcome = java.util.Objects.requireNonNull(outcome);
        this.occurredAt = java.util.Objects.requireNonNull(occurredAt);
    }
    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getOutcome() { return outcome; }
    public Instant getOccurredAt() { return occurredAt; }
}
