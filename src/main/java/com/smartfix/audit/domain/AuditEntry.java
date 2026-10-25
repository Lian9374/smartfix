package com.smartfix.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_entries")
public class AuditEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_user_id") private Long actorUserId;
    @Column(nullable = false, length = 80) private String action;
    @Column(nullable = false, name = "target_type", length = 30) private String targetType;
    @Column(nullable = false, name = "target_id") private Long targetId;
    @Column(nullable = false, length = 30) private String outcome;
    @Column(nullable = false, name = "occurred_at") private Instant occurredAt;

    protected AuditEntry() {}
    public static AuditEntry system(String action,String targetType,Long targetId,String outcome,Instant when){
        var entry=new AuditEntry();entry.action=java.util.Objects.requireNonNull(action);entry.targetType=java.util.Objects.requireNonNull(targetType);entry.targetId=java.util.Objects.requireNonNull(targetId);entry.outcome=java.util.Objects.requireNonNull(outcome);entry.occurredAt=java.util.Objects.requireNonNull(when);return entry;
    }
    public AuditEntry(Long actorUserId, String action, String targetType, Long targetId,
                      String outcome, Instant occurredAt) {
        this(new InitialState(actorUserId, action, targetType, targetId, outcome, occurredAt));
    }
    private AuditEntry(InitialState value) {
        this.actorUserId = value.actorUserId(); this.action = value.action();
        this.targetType = value.targetType(); this.targetId = value.targetId();
        this.outcome = value.outcome(); this.occurredAt = value.occurredAt();
    }
    // Validate before the entity's Object constructor, preventing partial construction.
    private record InitialState(Long actorUserId, String action, String targetType, Long targetId,
                                String outcome, Instant occurredAt) {
        InitialState {
            java.util.Objects.requireNonNull(actorUserId); java.util.Objects.requireNonNull(action);
            java.util.Objects.requireNonNull(targetType); java.util.Objects.requireNonNull(targetId);
            java.util.Objects.requireNonNull(outcome); java.util.Objects.requireNonNull(occurredAt);
        }
    }
    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getOutcome() { return outcome; }
    public Instant getOccurredAt() { return occurredAt; }
}
