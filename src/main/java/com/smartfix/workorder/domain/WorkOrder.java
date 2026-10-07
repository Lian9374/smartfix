package com.smartfix.workorder.domain;

import com.smartfix.common.exception.*;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "work_orders")
public class WorkOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true, updatable = false)
    private Long requestId;

    @Column(name = "technician_id", nullable = false)
    private Long technicianId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected WorkOrder() {}

    public static WorkOrder create(Long requestId, Long technicianId, Instant at) {
        WorkOrder w = new WorkOrder();
        w.requestId = Objects.requireNonNull(requestId);
        w.technicianId = Objects.requireNonNull(technicianId);
        w.status = WorkOrderStatus.CREATED;
        w.createdAt = Objects.requireNonNull(at);
        w.updatedAt = at;
        return w;
    }

    public void assignTo(Long technicianId, Instant at) {
        if (status == WorkOrderStatus.CLOSED)
            throw new BusinessConflictException("Work order is closed.");
        this.technicianId = Objects.requireNonNull(technicianId);
        syncStatus(WorkOrderStatus.CREATED, at);
        resolutionNote = null;
        completedAt = null;
    }

    public void syncStatus(WorkOrderStatus target, Instant at) {
        status = Objects.requireNonNull(target);
        updatedAt = at;
    }

    public void reopen(Instant at) {
        syncStatus(WorkOrderStatus.REOPENED, at);
        resolutionNote = null;
        completedAt = null;
    }

    public void touch(Instant at) {
        updatedAt = at;
    }

    public void complete(String note, Instant at) {
        if (status != WorkOrderStatus.IN_PROGRESS && status != WorkOrderStatus.ON_HOLD) {
            throw new BusinessConflictException("Start the work order before completing it.");
        }
        String normalized = note == null ? "" : note.trim();
        if (normalized.isEmpty() || normalized.length() > 500)
            throw new InputValidationException("Solution must contain 1–500 characters.");
        resolutionNote = normalized;
        completedAt = at;
        syncStatus(WorkOrderStatus.COMPLETED, at);
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
