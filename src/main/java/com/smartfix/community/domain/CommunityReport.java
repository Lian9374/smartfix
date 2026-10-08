package com.smartfix.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.Objects;

/**
 * One reader's report about one question or one answer.
 *
 * <h2>Exactly one target, and the database is what says so</h2>
 *
 * <p>{@code questionId} and {@code answerId} are both scalar ids and never JPA
 * associations, for the reason the rest of the module keeps ids scalar: the foreign
 * keys hold in the database while the Java modules stay independent. Exactly one of
 * them is set - never both, never neither - and the two factories below are the only
 * way to build a report, so a Java caller cannot construct the third case. That is a
 * convenience, not the guarantee: {@code V19}'s
 * {@code chk_community_reports_target} refuses a row that breaks the rule whichever
 * route it arrives by, and the same rule is repeated in the {@code @Check} below so
 * the schema the tests build carries it too.</p>
 *
 * <h2>Nothing here writes a decision</h2>
 *
 * <p>There is no setter and no {@code resolve} method. The four decision columns -
 * {@code status}, {@code handledByUserId}, {@code handledAt}, {@code resolutionNote} -
 * are written by exactly one statement,
 * {@code CommunityReportRepository.resolveIfOpen}, and by nothing else. That is the
 * same arrangement, for the same reason, as
 * {@link CommunityQuestion#getAcceptedAnswerId()}: the column is absent from every
 * entity {@code INSERT} and {@code UPDATE} Hibernate generates, so a moderator who
 * loaded a report before somebody else settled it cannot flush a stale decision over
 * the one that took effect. "A settled decision is never overwritten" then rests on
 * the conditional statement's {@code WHERE} clause rather than on every future caller
 * remembering to check first.</p>
 *
 * <h2>One report per reporter per target</h2>
 *
 * <p>{@code V19}'s two partial unique indexes make that permanent, including across
 * the handling of the first report. The service checks for a duplicate before it
 * inserts so that the ordinary case gets a readable sentence rather than a database
 * error; the indexes are what make the concurrent case produce one row and one
 * refusal instead of two rows. See the migration and the contract document for why
 * re-reporting after a decision is out of scope, and for the one-line change that
 * would allow it.</p>
 */
@Entity
@Table(
        name = "community_reports",
        // Declared on the entity as well as in V19. PostgreSQL's schema comes from the
        // migration and never from Hibernate, so this pair only materialises in the H2
        // database the tests build from the mappings - where it gives the duplicate-insert
        // rule a real backstop instead of leaving it entirely to the service's check. The
        // semantics are the same on both sides: NULLs are distinct in a unique constraint,
        // so a question report never collides with an answer report in either spelling.
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_community_reports_question",
                        columnNames = {"reporter_id", "question_id"}),
                @UniqueConstraint(name = "uk_community_reports_answer",
                        columnNames = {"reporter_id", "answer_id"})
        })
@Check(constraints = "(question_id IS NOT NULL AND answer_id IS NULL)"
        + " OR (question_id IS NULL AND answer_id IS NOT NULL)")
public class CommunityReport {

    /** Longest note a reporter may add, and longest note a moderator may leave. */
    public static final int DETAIL_MAX_LENGTH = 500;

    public static final int NOTE_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", updatable = false)
    private Long questionId;

    @Column(name = "answer_id", updatable = false)
    private Long answerId;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30, updatable = false)
    private CommunityReportReason reason;

    @Column(name = "detail", length = DETAIL_MAX_LENGTH, updatable = false)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommunityReportStatus status;

    @Column(name = "handled_by_user_id")
    private Long handledByUserId;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "resolution_note", length = NOTE_MAX_LENGTH)
    private String resolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. Application code must use the two factories below. */
    protected CommunityReport() {
        // no-op
    }

    private CommunityReport(
            Long questionId,
            Long answerId,
            Long reporterId,
            CommunityReportReason reason,
            String detail,
            Instant createdAt) {
        this.questionId = questionId;
        this.answerId = answerId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.detail = detail;
        this.status = CommunityReportStatus.OPEN;
        this.createdAt = createdAt;
    }

    /**
     * Reports a question.
     *
     * <p>{@code reporterId} comes from the trusted authenticated principal, exactly as
     * authorship does everywhere else in this module: the command DTO has no reporter
     * field for a browser to forge.</p>
     */
    public static CommunityReport ofQuestion(
            Long reporterId,
            Long questionId,
            CommunityReportReason reason,
            String detail,
            Instant createdAt) {
        Objects.requireNonNull(questionId, "questionId");
        return new CommunityReport(
                questionId,
                null,
                requireReporter(reporterId),
                requireReason(reason),
                normalizeDetail(detail),
                requireCreatedAt(createdAt));
    }

    /** Reports one answer. The answer's question is deliberately not recorded here. */
    public static CommunityReport ofAnswer(
            Long reporterId,
            Long answerId,
            CommunityReportReason reason,
            String detail,
            Instant createdAt) {
        Objects.requireNonNull(answerId, "answerId");
        return new CommunityReport(
                null,
                answerId,
                requireReporter(reporterId),
                requireReason(reason),
                normalizeDetail(detail),
                requireCreatedAt(createdAt));
    }

    /** Whether a moderator may still record a decision for this report. */
    public boolean isOpen() {
        return status != null && status.isOpen();
    }

    /** Whether this report is about a question rather than an answer. */
    public boolean targetsQuestion() {
        return questionId != null;
    }

    /** Which kind of content was reported. */
    public CommunityContentType getTargetType() {
        return targetsQuestion() ? CommunityContentType.QUESTION : CommunityContentType.ANSWER;
    }

    /** The id of the reported content, whichever kind it is. */
    public Long getTargetId() {
        return targetsQuestion() ? questionId : answerId;
    }

    private static Long requireReporter(Long reporterId) {
        if (reporterId == null) {
            throw new IllegalArgumentException("reporterId is required");
        }
        return reporterId;
    }

    private static CommunityReportReason requireReason(CommunityReportReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("reason is required");
        }
        return reason;
    }

    private static Instant requireCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        return createdAt;
    }

    /**
     * Trims the note, and reads a blank one as no note at all.
     *
     * <p>Empty and absent mean the same thing to a moderator, and storing {@code ""}
     * for one of them would put two spellings of "nothing" in the column that
     * {@code V19}'s {@code chk_community_reports_detail} then has to allow. Normalizing
     * here and refusing the empty string in the database keeps the two agreeing.</p>
     */
    private static String normalizeDetail(String detail) {
        if (detail == null) {
            return null;
        }
        String normalized = detail.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > DETAIL_MAX_LENGTH) {
            throw new IllegalArgumentException("detail must contain at most "
                    + DETAIL_MAX_LENGTH + " characters");
        }
        return normalized;
    }

    public Long getId() {
        return id;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getAnswerId() {
        return answerId;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public CommunityReportReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public CommunityReportStatus getStatus() {
        return status;
    }

    public Long getHandledByUserId() {
        return handledByUserId;
    }

    public Instant getHandledAt() {
        return handledAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CommunityReport report) || id == null) {
            return false;
        }
        return id.equals(report.id);
    }

    @Override
    public int hashCode() {
        return CommunityReport.class.hashCode();
    }
}
