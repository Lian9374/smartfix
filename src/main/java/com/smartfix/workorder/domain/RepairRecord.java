package com.smartfix.workorder.domain;

import com.smartfix.common.exception.InputValidationException;
import com.smartfix.workorder.dto.RepairRecordCommand;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "repair_records")
public class RepairRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_id", nullable = false, updatable = false)
    private Long workOrderId;

    @Column(name = "technician_id", nullable = false, updatable = false)
    private Long technicianId;

    @Column(nullable = false, length = 2000, updatable = false)
    private String diagnosis;

    @Column(name = "action_taken", nullable = false, length = 2000, updatable = false)
    private String actionTaken;

    @Column(name = "materials_used", length = 1000, updatable = false)
    private String materialsUsed;

    @Column(name = "minutes_spent", nullable = false, updatable = false)
    private int minutesSpent;

    @Column(name = "evidence_attachment_id", updatable = false)
    private Long evidenceAttachmentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RepairRecord() {}

    public static RepairRecord create(
            Long orderId, Long actorId, RepairRecordCommand c, Instant at) {
        if (c == null || c.getMinutesSpent() == null || c.getMinutesSpent() < 1) {
            throw new InputValidationException("Time spent must be a positive number of minutes.");
        }
        RepairRecord r = new RepairRecord();
        r.workOrderId = orderId;
        r.technicianId = actorId;
        r.diagnosis = text(c.getDiagnosis(), 2000, true);
        r.actionTaken = text(c.getActionTaken(), 2000, true);
        r.materialsUsed = text(c.getMaterialsUsed(), 1000, false);
        r.minutesSpent = c.getMinutesSpent();
        r.evidenceAttachmentId = c.getEvidenceAttachmentId();
        r.createdAt = at;
        return r;
    }

    private static String text(String value, int limit, boolean required) {
        String result = value == null ? "" : value.trim();
        if ((required && result.isEmpty()) || result.length() > limit)
            throw new InputValidationException("Invalid repair record field length.");
        return result;
    }

    public Long getId() {
        return id;
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public String getMaterialsUsed() {
        return materialsUsed;
    }

    public int getMinutesSpent() {
        return minutesSpent;
    }

    public Long getEvidenceAttachmentId() {
        return evidenceAttachmentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
