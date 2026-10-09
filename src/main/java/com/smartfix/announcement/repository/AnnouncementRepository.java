package com.smartfix.announcement.repository;

import com.smartfix.announcement.domain.Announcement;
import com.smartfix.announcement.domain.AnnouncementStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AnnouncementRepository
    extends JpaRepository<Announcement, Long> {

    @Query("""
            SELECT a
            FROM Announcement a
            WHERE a.status = :status
              AND a.validFrom <= :now
              AND (a.validTo IS NULL OR a.validTo > :now)
            ORDER BY a.validFrom DESC, a.id DESC
            """)
    List<Announcement> findVisible(
        @Param("status") AnnouncementStatus status,
        @Param("now") Instant now);

    List<Announcement> findAllByOrderByCreatedAtDescIdDesc();
}
