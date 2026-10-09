package com.smartfix.announcement.domain;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "announcements")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 4000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementStatus status;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected Announcement() {
        // Required by JPA.
    }

    public static Announcement create(
        String title,
        String content,
        Instant validFrom,
        Instant validTo,
        Long createdBy,
        Instant now) {

        if (title == null || title.isBlank()
            || title.trim().length() > 150) {
            throw new InputValidationException(
                "Announcement title must contain 1 to 150 characters.");
        }

        if (content == null || content.isBlank()
            || content.trim().length() > 4000) {
            throw new InputValidationException(
                "Announcement content must contain 1 to 4000 characters.");
        }

        if (validFrom == null
            || (validTo != null && !validTo.isAfter(validFrom))) {
            throw new InputValidationException(
                "Announcement validity period is invalid.");
        }

        if (createdBy == null || createdBy <= 0 || now == null) {
            throw new InputValidationException(
                "A valid announcement creator and timestamp are required.");
        }

        Announcement announcement = new Announcement();

        announcement.title = title.trim();
        announcement.content = content.trim();
        announcement.status = AnnouncementStatus.DRAFT;
        announcement.validFrom = validFrom;
        announcement.validTo = validTo;
        announcement.createdBy = createdBy;
        announcement.createdAt = now;
        announcement.updatedAt = now;

        return announcement;
    }

    public void publish(Instant now) {
        if (status != AnnouncementStatus.DRAFT) {
            throw new BusinessConflictException(
                "Only draft announcements can be published.");
        }

        if (now == null) {
            throw new InputValidationException(
                "Publication time is required.");
        }

        status = AnnouncementStatus.PUBLISHED;
        publishedAt = now;
        updatedAt = now;
    }

    public void withdraw(Instant now) {
        if (status != AnnouncementStatus.PUBLISHED) {
            throw new BusinessConflictException(
                "Only published announcements can be withdrawn.");
        }

        if (now == null) {
            throw new InputValidationException(
                "Withdrawal time is required.");
        }

        status = AnnouncementStatus.WITHDRAWN;
        withdrawnAt = now;
        updatedAt = now;
    }

    public boolean isVisibleAt(Instant now) {
        return now != null
            && status == AnnouncementStatus.PUBLISHED
            && !now.isBefore(validFrom)
            && (validTo == null || now.isBefore(validTo));
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public AnnouncementStatus getStatus() {
        return status;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }
}
