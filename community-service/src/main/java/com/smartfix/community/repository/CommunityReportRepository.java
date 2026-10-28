package com.smartfix.community.repository;

import com.smartfix.community.domain.CommunityReport;
import com.smartfix.community.domain.CommunityReportStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * Persistence owned by the community module for reports.
 *
 * <h2>The queue's order, in the method name</h2>
 *
 * <p>{@code findByStatusOrderByCreatedAtAscIdAsc} keeps the ordering rule where a
 * reader can see it rather than in an argument the caller could forget: oldest first,
 * because the moderator's question is what has been waiting longest, with the id as a
 * tie-breaker for the reason every other list in the module carries one. It matches
 * {@code idx_community_reports_queue} in {@code V19} exactly.</p>
 *
 * <h2>The only writer of a decision</h2>
 *
 * <p>{@code resolveIfOpen} is the single statement that can settle a report. It is
 * conditional on the report still being {@code OPEN}, so two moderators acting on the
 * same report at the same time produce one decision and one no-op, and the no-op
 * cannot overwrite what the first one recorded - there is no read, no version and no
 * retry, only an {@code UPDATE} that matches a row once. That is the same shape as
 * {@code CommunityQuestionRepository.acceptAnswerIfOpen}, and it is here for the same
 * reason.</p>
 *
 * <p>The affected-row count is the answer to "did this call decide anything", which
 * is why it is returned rather than discarded: callers need it to avoid claiming a
 * decision that a concurrent moderator had already taken, and to avoid acting on the
 * content of a report somebody else had already settled.</p>
 */
public interface CommunityReportRepository extends JpaRepository<CommunityReport, Long> {

    /**
     * Whether this account has already reported this question.
     *
     * <p>The friendly half of the duplicate rule: the service asks before it inserts so
     * the ordinary case is a readable sentence rather than a constraint violation. It is
     * not the rule itself - two simultaneous reports both pass this check - which is why
     * the unique index behind it still has to exist.</p>
     */
    boolean existsByReporterIdAndQuestionId(Long reporterId, Long questionId);

    /** Whether this account has already reported this answer. See the question variant. */
    boolean existsByReporterIdAndAnswerId(Long reporterId, Long answerId);

    /** One page of the moderation queue: the oldest reports still waiting, first. */
    Page<CommunityReport> findByStatusOrderByCreatedAtAscIdAsc(
            CommunityReportStatus status, Pageable pageable);

    Page<CommunityReport> findByStatusInOrderByCreatedAtDescIdDesc(
            java.util.Collection<CommunityReportStatus> statuses, Pageable pageable);

    /**
     * How many reports are in one state.
     *
     * <p>Read for the counts the queue shows, and never for a decision: whether a
     * report may still be resolved is settled by {@link #resolveIfOpen}'s
     * {@code WHERE} clause, not by a count that another transaction can invalidate
     * between the read and the write.</p>
     */
    long countByStatus(CommunityReportStatus status);

    /**
     * Records a moderator's decision, but only if the report is still open.
     *
     * <p>All four decision columns move together, so a settled report is never half
     * decided, and {@code V19}'s {@code chk_community_reports_handled} refuses a row
     * where the status and the time disagree.</p>
     *
     * <p>{@code @Modifying(clearAutomatically = true)} matters here: the entity that
     * was read before this call would otherwise still be in the persistence context
     * holding the pre-decision state, and a caller that read it again in the same
     * transaction would see a report that looks open when it is not.</p>
     *
     * @return 1 when this call settled the report, 0 when somebody else already had
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE CommunityReport r
            SET r.status = :decision,
                r.handledByUserId = :actorUserId,
                r.handledAt = :now,
                r.resolutionNote = :note
            WHERE r.id = :reportId
              AND r.status = com.smartfix.community.domain.CommunityReportStatus.OPEN
            """)
    int resolveIfOpen(
            @Param("reportId") Long reportId,
            @Param("decision") CommunityReportStatus decision,
            @Param("note") String note,
            @Param("actorUserId") Long actorUserId,
            @Param("now") Instant now);
}
