package com.smartfix.announcement.service;

import com.smartfix.announcement.domain.Announcement;
import com.smartfix.announcement.domain.AnnouncementStatus;
import com.smartfix.announcement.repository.AnnouncementRepository;
import com.smartfix.common.exception.BusinessConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnouncementServiceTest {

    private AnnouncementRepository repository;
    private AnnouncementService service;

    private final Instant now =
        Instant.parse("2026-10-09T08:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(AnnouncementRepository.class);

        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        service = new AnnouncementService(repository, clock);
    }

    @Test
    void createsDraftAnnouncement() {
        Announcement announcement = Announcement.create(
            "Maintenance Notice",
            "The library air conditioning will be serviced.",
            now.minusSeconds(3600),
            now.plusSeconds(86400),
            1L,
            now);

        when(repository.save(any(Announcement.class)))
            .thenAnswer(invocation ->
                invocation.getArgument(0));

        var result = service.createDraft(
            "Maintenance Notice",
            "The library air conditioning will be serviced.",
            now.minusSeconds(3600),
            now.plusSeconds(86400),
            1L);

        assertEquals(AnnouncementStatus.DRAFT, result.status());
        assertEquals("Maintenance Notice", result.title());

        verify(repository).save(any(Announcement.class));
    }

    @Test
    void publishesDraftAnnouncement() {
        Announcement announcement = createDraft();

        when(repository.findById(1L))
            .thenReturn(Optional.of(announcement));

        when(repository.save(announcement))
            .thenReturn(announcement);

        var result = service.publish(1L);

        assertEquals(AnnouncementStatus.PUBLISHED, result.status());
        assertEquals(now, result.publishedAt());
    }

    @Test
    void withdrawsPublishedAnnouncement() {
        Announcement announcement = createDraft();
        announcement.publish(now.minusSeconds(60));

        when(repository.findById(1L))
            .thenReturn(Optional.of(announcement));

        when(repository.save(announcement))
            .thenReturn(announcement);

        var result = service.withdraw(1L);

        assertEquals(AnnouncementStatus.WITHDRAWN, result.status());
        assertEquals(now, result.withdrawnAt());
    }

    @Test
    void rejectsRepeatedPublication() {
        Announcement announcement = createDraft();
        announcement.publish(now.minusSeconds(60));

        when(repository.findById(1L))
            .thenReturn(Optional.of(announcement));

        assertThrows(
            BusinessConflictException.class,
            () -> service.publish(1L));
    }

    @Test
    void visibleAnnouncementsOnlyIncludePublishedAndValid() {
        Announcement announcement = createDraft();
        announcement.publish(now.minusSeconds(60));

        when(repository.findVisible(
            AnnouncementStatus.PUBLISHED, now))
            .thenReturn(List.of(announcement));

        var result = service.listVisible();

        assertEquals(1, result.size());
        assertEquals(AnnouncementStatus.PUBLISHED,
            result.get(0).status());

        verify(repository).findVisible(
            AnnouncementStatus.PUBLISHED, now);
    }

    @Test
    void publishedAnnouncementIsVisibleOnlyWithinValidPeriod() {
        Announcement announcement = createDraft();
        announcement.publish(now.minusSeconds(60));

        assertTrue(announcement.isVisibleAt(now));
        assertTrue(announcement.isVisibleAt(now.minusSeconds(3600)));

        assertFalse(announcement.isVisibleAt(
            now.minusSeconds(3601)));

        assertFalse(announcement.isVisibleAt(
            now.plusSeconds(86400)));
    }

    @Test
    void draftAndWithdrawnAnnouncementsAreNotVisible() {
        Announcement announcement = createDraft();

        assertFalse(announcement.isVisibleAt(now));

        announcement.publish(now.minusSeconds(60));
        assertTrue(announcement.isVisibleAt(now));

        announcement.withdraw(now);

        assertFalse(announcement.isVisibleAt(now));
    }

    @Test
    void announcementWithoutExpiryRemainsVisibleAfterStart() {
        Announcement announcement = Announcement.create(
            "Campus Notice",
            "Library opening hours have changed.",
            now.minusSeconds(3600),
            null,
            1L,
            now.minusSeconds(7200));

        announcement.publish(now.minusSeconds(60));

        assertTrue(announcement.isVisibleAt(now));
        assertTrue(announcement.isVisibleAt(
            now.plusSeconds(86400 * 30L)));
    }

    private Announcement createDraft() {
        return Announcement.create(
            "Maintenance Notice",
            "Scheduled maintenance.",
            now.minusSeconds(3600),
            now.plusSeconds(86400),
            1L,
            now.minusSeconds(7200));
    }
}
