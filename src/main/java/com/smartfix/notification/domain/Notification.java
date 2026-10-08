package com.smartfix.notification.domain;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    private Notification(
            Long recipientId,
            String eventType,
            String title,
            String message,
            Long referenceId,
            Instant createdAt
    ) {
        this.recipientId = Objects.requireNonNull(recipientId);
        this.eventType = Objects.requireNonNull(eventType);
        this.title = Objects.requireNonNull(title);
        this.message = Objects.requireNonNull(message);
        this.referenceId = referenceId;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Notification create(
            Long recipientId,
            String eventType,
            String title,
            String message,
            Long referenceId,
            Instant createdAt
    ) {
        return new Notification(
                recipientId,
                eventType,
                title,
                message,
                referenceId,
                createdAt
        );
    }

    public void markAsRead(Instant readAt) {
        if (this.readAt == null) {
            this.readAt = Objects.requireNonNull(readAt);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** Only generate links for a known event; never accept a user-supplied URL. */
    public String getTargetUrl() {
        return referenceId != null && ("COMMUNITY_ANSWER_CREATED".equals(eventType)
                || "COMMUNITY_ANSWER_ACCEPTED".equals(eventType))
                ? "/community/questions/" + referenceId : null;
    }
}
