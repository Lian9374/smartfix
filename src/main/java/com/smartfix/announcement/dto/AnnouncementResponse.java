package com.smartfix.announcement.dto;

import com.smartfix.announcement.domain.Announcement;
import com.smartfix.announcement.domain.AnnouncementStatus;

import java.time.Instant;

public record AnnouncementResponse(
    Long id,
    String title,
    String content,
    AnnouncementStatus status,
    Instant validFrom,
    Instant validTo,
    Instant createdAt,
    Instant publishedAt,
    Instant withdrawnAt) {

    public static AnnouncementResponse from(Announcement announcement) {
        return new AnnouncementResponse(
            announcement.getId(),
            announcement.getTitle(),
            announcement.getContent(),
            announcement.getStatus(),
            announcement.getValidFrom(),
            announcement.getValidTo(),
            announcement.getCreatedAt(),
            announcement.getPublishedAt(),
            announcement.getWithdrawnAt());
    }
}
