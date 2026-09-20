package com.smartfix.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/** An immutable audit entry recording one maintenance-request status change. */
@Entity
@Table(name = "request_status_history")
public class RequestStatusHistory {

    public static final int COMMENT_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private RequestStatus toStatus;

    @Column(name = "changed_by_user_id", nullable = false, updatable = false)
    private Long changedByUserId;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "comment", length = COMMENT_MAX_LENGTH, updatable = false)
    private String comment;

    /** Required by JPA. Application code must use a named factory. */
    protected RequestStatusHistory() {
        // no-op
    }

    private RequestStatusHistory(Long requestId,
                                 RequestStatus fromStatus,
                                 RequestStatus toStatus,
                                 Long changedByUserId,
                                 Instant changedAt,
                                 String comment) {
        this.requestId = requestId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByUserId = changedByUserId;
        this.changedAt = changedAt;
        this.comment = comment;
    }

    /** Creates the mandatory {@code NULL -> SUBMITTED} audit entry. */
    public static RequestStatusHistory initialSubmission(Long requestId,
                                                         Long changedByUserId,
                                                         Instant changedAt) {
        return new RequestStatusHistory(
                Objects.requireNonNull(requestId, "requestId"),
                null,
                RequestStatus.SUBMITTED,
                Objects.requireNonNull(changedByUserId, "changedByUserId"),
                Objects.requireNonNull(changedAt, "changedAt"),
                null);
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public RequestStatus getFromStatus() {
        return fromStatus;
    }

    public RequestStatus getToStatus() {
        return toStatus;
    }

    public Long getChangedByUserId() {
        return changedByUserId;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RequestStatusHistory history) || id == null) {
            return false;
        }
        return id.equals(history.id);
    }

    @Override
    public int hashCode() {
        return RequestStatusHistory.class.hashCode();
    }
}
