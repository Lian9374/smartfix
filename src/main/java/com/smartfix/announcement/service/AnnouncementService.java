package com.smartfix.announcement.service;

import com.smartfix.announcement.domain.Announcement;
import com.smartfix.announcement.domain.AnnouncementStatus;
import com.smartfix.announcement.dto.AnnouncementResponse;
import com.smartfix.announcement.repository.AnnouncementRepository;
import com.smartfix.common.exception.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AnnouncementService {

    private final AnnouncementRepository announcements;
    private final Clock clock;

    public AnnouncementService(
        AnnouncementRepository announcements,
        Clock clock) {

        this.announcements = announcements;
        this.clock = clock;
    }

    public List<AnnouncementResponse> listVisible() {
        Instant now = clock.instant();

        return announcements
            .findVisible(AnnouncementStatus.PUBLISHED, now)
            .stream()
            .map(AnnouncementResponse::from)
            .toList();
    }

    public List<AnnouncementResponse> listAll() {
        return announcements
            .findAllByOrderByCreatedAtDescIdDesc()
            .stream()
            .map(AnnouncementResponse::from)
            .toList();
    }

    @Transactional
    public AnnouncementResponse createDraft(
        String title,
        String content,
        Instant validFrom,
        Instant validTo,
        Long creatorUserId) {

        Announcement announcement = Announcement.create(
            title,
            content,
            validFrom,
            validTo,
            creatorUserId,
            clock.instant());

        return AnnouncementResponse.from(
            announcements.save(announcement));
    }

    @Transactional
    public AnnouncementResponse publish(Long id) {
        Announcement announcement = requireAnnouncement(id);

        announcement.publish(clock.instant());

        return AnnouncementResponse.from(
            announcements.save(announcement));
    }

    @Transactional
    public AnnouncementResponse withdraw(Long id) {
        Announcement announcement = requireAnnouncement(id);

        announcement.withdraw(clock.instant());

        return AnnouncementResponse.from(
            announcements.save(announcement));
    }

    private Announcement requireAnnouncement(Long id) {
        return announcements.findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Announcement not found"));
    }
}
