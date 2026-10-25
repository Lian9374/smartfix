package com.smartfix.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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

    @Column(name = "dedup_key", nullable = false, unique = true, length = 200)
    private String dedupKey;

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
            Instant createdAt,
            String dedupKey
    ) {
        this.recipientId = recipientId;
        this.eventType = eventType;
        this.title = title;
        this.message = message;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
        this.dedupKey = dedupKey;
    }

    public static Notification create(
            Long recipientId,
            String eventType,
            String title,
            String message,
            Long referenceId,
            Instant createdAt
    ) {
        return create(recipientId, eventType, title, message, referenceId, createdAt,
                "COMMUNITY_DELIVERY:" + UUID.randomUUID());
    }

    public static Notification create(Long recipientId, String eventType, String title, String message,
            Long referenceId, Instant createdAt, String dedupKey) {
        return new Notification(
                Objects.requireNonNull(recipientId),
                requireText(eventType, 80),
                requireText(title, 150),
                requireText(message, 1000),
                referenceId,
                Objects.requireNonNull(createdAt),
                requireText(dedupKey, 200)
        );
    }

    private static String requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("Invalid notification field");
        }
        return value;
    }

    public String getDedupKey() {
        return dedupKey;
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
        if(referenceId!=null&&("SLA_BREACHED".equals(eventType)||"SLA_DUE_SOON".equals(eventType)))return "/admin/sla";
        return referenceId != null && ("COMMUNITY_ANSWER_CREATED".equals(eventType)
                || "COMMUNITY_ANSWER_ACCEPTED".equals(eventType))
                ? "/community/questions/" + referenceId : null;
    }
}
