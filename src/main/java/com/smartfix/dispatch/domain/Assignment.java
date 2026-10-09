package com.smartfix.dispatch.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Assignment history is retained; reassignment deactivates a row and creates a new one. */
@Entity
@Table(name = "assignments")
public class Assignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;
    @Column(name = "technician_id", nullable = false, updatable = false)
    private Long technicianId;
    @Column(name = "assigned_by_user_id", nullable = false, updatable = false)
    private Long assignedByUserId;
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;
    @Column(length = 500, updatable = false)
    private String reason;
    @Column(nullable = false)
    private boolean active;
    @Version @Column(nullable = false)
    private long version;
    @Column(name = "deactivated_by_user_id")
    private Long deactivatedByUserId;
    @Column(name = "deactivated_at")
    private Instant deactivatedAt;
    @Column(name = "deactivation_reason", length = 500)
    private String deactivationReason;

    protected Assignment() { }

    public static Assignment create(Long requestId, Long technicianId, Long actorId, String reason, Instant now) {
        Assignment assignment = new Assignment();
        assignment.requestId = requestId;
        assignment.technicianId = technicianId;
        assignment.assignedByUserId = actorId;
        assignment.assignedAt = now;
        assignment.reason = reason;
        assignment.active = true;
        return assignment;
    }

    public void deactivate(Long actorId, String reason, Instant now) {
        active = false;
        deactivatedByUserId = actorId;
        deactivationReason = reason;
        deactivatedAt = now;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public Long getTechnicianId() { return technicianId; }
    public Long getAssignedByUserId() { return assignedByUserId; }
    public Instant getAssignedAt() { return assignedAt; }
    public String getReason() { return reason; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
    public Long getDeactivatedByUserId() { return deactivatedByUserId; }
    public Instant getDeactivatedAt() { return deactivatedAt; }
    public String getDeactivationReason() { return deactivationReason; }
}
