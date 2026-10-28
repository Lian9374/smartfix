package com.smartfix.community.domain;

import com.smartfix.common.exception.BusinessConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

/**
 * An answer posted against one {@link CommunityQuestion}.
 *
 * <p>Both {@code questionId} and {@code authorId} are scalar ids, not cross-module or
 * even cross-aggregate JPA associations. The answer does not hold a reference back to
 * its question object: a question's answers are read through
 * {@code CommunityAnswerRepository}, which keeps the aggregate boundary one-directional
 * and means persisting an answer never cascades into the question row.</p>
 *
 * <p>Answers are written through {@code CommunityAnswerService} and reach the board
 * through the answer routes on the question thread. The entity holds the body and the
 * status and nothing else: whether an answer is the question's accepted one lives on the
 * question, so accepting never writes this row and cannot leave the two disagreeing about
 * which answer was chosen.</p>
 */
@Entity
@Table(name = "community_answers")
public class CommunityAnswer {

    public static final int BODY_MIN_LENGTH = 10;
    public static final int BODY_MAX_LENGTH = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false, updatable = false)
    private Long questionId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(name = "body", nullable = false, length = BODY_MAX_LENGTH)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommunityContentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    /** Required by JPA. Application code must use {@link #post}. */
    protected CommunityAnswer() {
        // no-op
    }

    private CommunityAnswer(Long questionId, Long authorId, String body, Instant createdAt) {
        this.questionId = questionId;
        this.authorId = authorId;
        this.body = body;
        this.status = CommunityContentStatus.VISIBLE;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Posts an answer to a question.
     *
     * <p>{@code authorId} comes from the trusted authenticated principal, exactly as it
     * does for {@link CommunityQuestion#ask}: the command DTO has no author field for a
     * browser to forge.</p>
     */
    public static CommunityAnswer post(Long questionId, Long authorId, String body, Instant createdAt) {
        Objects.requireNonNull(questionId, "questionId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(createdAt, "createdAt");
        return new CommunityAnswer(
                questionId,
                authorId,
                requireBody(body),
                createdAt);
    }

    /** Rewrites the body of an answer its author may still change. */
    public void edit(String body, Instant at) {
        Objects.requireNonNull(at, "at");
        if (status != CommunityContentStatus.VISIBLE) {
            throw new BusinessConflictException(
                    "This answer can no longer be edited because it is not publicly visible.");
        }
        this.body = requireBody(body);
        this.updatedAt = at;
    }

    /**
     * Takes the answer out of public view at its author's request.
     *
     * <p>The row is retained, for the reasons given on {@link CommunityQuestion#withdraw}.
     * Clearing {@code community_questions.accepted_answer_id} when the withdrawn answer
     * was the accepted one is the caller's job, inside the same transaction, so the
     * derived solved state cannot drift.</p>
     */
    public void withdraw(Instant at) {
        Objects.requireNonNull(at, "at");
        if (status == CommunityContentStatus.WITHDRAWN) {
            return;
        }
        status = CommunityContentStatus.WITHDRAWN;
        updatedAt = at;
    }

    /**
     * Takes the answer out of public view at a moderator's decision.
     *
     * <p>Only {@code VISIBLE} content can be hidden. Hiding something already hidden
     * changes nothing and raises no event, which is what makes a double-submitted
     * moderation form a no-op rather than a second notification; hiding something its
     * author has already withdrawn is a no-op the same way, because an administrator's
     * decision is not the author's to reverse and the record of who took the content
     * down should not be rewritten by an action that has no visible effect anyway.</p>
     *
     * <p>If this answer is the question's accepted answer, clearing that acceptance is
     * the caller's job, inside the same transaction - exactly as {@link #withdraw}
     * documents. A hidden answer cannot stay the accepted one: the question would read
     * as solved with nothing readable under it, and the clearing statement is
     * conditional so it cannot remove an acceptance that a concurrent operation has
     * already replaced.</p>
     *
     * @return whether this call changed the status
     */
    public boolean hide(Instant at) {
        Objects.requireNonNull(at, "at");
        if (status != CommunityContentStatus.VISIBLE) {
            return false;
        }
        status = CommunityContentStatus.HIDDEN;
        updatedAt = at;
        return true;
    }

    /**
     * Returns a hidden answer to public view.
     *
     * <p>Only {@code HIDDEN} content can be restored, and that is the whole of the
     * rule: an answer its author withdrew stays withdrawn, because an administrator
     * reversing a moderation decision must not also undo somebody's own decision to
     * take their words down. Nothing else is restored either - an answer that was
     * accepted, then hidden, then restored comes back visible and <em>not</em>
     * accepted, because acceptance is set by one statement and cleared by another and
     * no restore is either of them.</p>
     *
     * @return whether this call changed the status
     */
    public boolean restore(Instant at) {
        Objects.requireNonNull(at, "at");
        if (status != CommunityContentStatus.HIDDEN) {
            return false;
        }
        status = CommunityContentStatus.VISIBLE;
        updatedAt = at;
        return true;
    }

    public boolean isPubliclyVisible() {
        return status != null && status.isPubliclyVisible();
    }

    private static String requireBody(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.length() < BODY_MIN_LENGTH
                || normalized.length() > BODY_MAX_LENGTH) {
            throw new IllegalArgumentException("body must contain between "
                    + BODY_MIN_LENGTH + " and " + BODY_MAX_LENGTH + " characters");
        }
        return normalized;
    }

    public Long getId() {
        return id;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }

    public CommunityContentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CommunityAnswer answer) || id == null) {
            return false;
        }
        return id.equals(answer.id);
    }

    @Override
    public int hashCode() {
        return CommunityAnswer.class.hashCode();
    }
}
