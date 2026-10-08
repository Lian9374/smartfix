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
 * Aggregate root for a question posted to the campus community board.
 *
 * <p>Owned by the {@code community} module. {@code authorId} is a scalar id, never a
 * cross-module JPA association, for the same reason the request module keeps
 * {@code requesterId} scalar: the foreign key holds in the database while the Java
 * modules stay independently maintainable.</p>
 *
 * <h2>Solved is derived, never stored</h2>
 *
 * <p>There is no {@code solved} boolean. A question counts as solved exactly when
 * {@link #getAcceptedAnswerId()} is non-null, and only accepting an answer can set it,
 * so a stored flag could only ever disagree with the answer it is supposed to
 * describe. The sprint 3 brief forbids the column for the same reason.</p>
 *
 * <h2>The accepted answer must belong to this question</h2>
 *
 * <p>{@code accepted_answer_id} carries no JPA association and, deliberately, no
 * single-column foreign key. The database enforces the stronger rule instead - the
 * accepted answer must be an answer <em>to this question</em> - through the composite
 * foreign key {@code (accepted_answer_id, id) -> community_answers (id, question_id)}
 * added by migration {@code V18}. See {@code V17} for why it cannot be declared at
 * table-creation time.</p>
 */
@Entity
@Table(name = "community_questions")
public class CommunityQuestion {

    public static final int TITLE_MIN_LENGTH = 3;
    public static final int TITLE_MAX_LENGTH = 150;
    public static final int BODY_MIN_LENGTH = 10;
    public static final int BODY_MAX_LENGTH = 4000;
    /** Longest search term the query layer will accept; see {@code CommunityQueryService}. */
    public static final int SEARCH_TERM_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "body", nullable = false, length = BODY_MAX_LENGTH)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private CommunityCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommunityContentStatus status;

    /**
     * Id of the answer its author accepted, or {@code null} while the question is open.
     *
     * <h2>Read here, written only by the two conditional statements</h2>
     *
     * <p>{@code insertable = false, updatable = false} is not a detail of persistence
     * plumbing; it is the guarantee. Hibernate omits the column from every {@code INSERT}
     * and every entity {@code UPDATE} it generates, so <em>no</em> entity write path can
     * change an accepted answer - not an edit, not a withdrawal, not a future moderation
     * restore, and not a writer that loaded the question before somebody else accepted an
     * answer and is about to flush a stale copy over it.</p>
     *
     * <p>The column's only writers are {@code CommunityQuestionRepository}'s
     * {@code acceptAnswerIfOpen} and {@code clearAcceptanceIfPresent}, which are
     * conditional SQL statements: the first sets it only while it is still {@code NULL},
     * the second clears it only while it is set. That is what makes "at most one accepted
     * answer per question" a property the database enforces rather than one the service
     * remembers to check.</p>
     *
     * <p>A question is therefore always created open - there is no way to pass an
     * accepted answer to {@link #ask} - and the browser command DTO has no field that
     * could carry one.</p>
     */
    @Column(name = "accepted_answer_id", insertable = false, updatable = false)
    private Long acceptedAnswerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    /** Required by JPA. Application code must use {@link #ask}. */
    protected CommunityQuestion() {
        // no-op
    }

    private CommunityQuestion(
            Long authorId,
            String title,
            String body,
            CommunityCategory category,
            Instant createdAt) {
        this.authorId = authorId;
        this.title = title;
        this.body = body;
        this.category = category;
        this.status = CommunityContentStatus.VISIBLE;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Posts a new question.
     *
     * <p>{@code authorId} is supplied by the trusted authenticated principal in the
     * service layer. It is deliberately absent from the browser command DTO, so a
     * forged {@code authorId} field in a submitted form has nowhere to bind.</p>
     *
     * <p>{@code acceptedAnswerId} is absent for the same reason: a question is created
     * open, and only the accept flow - owner-only, a later phase - may set it.</p>
     */
    public static CommunityQuestion ask(
            Long authorId,
            String title,
            String body,
            CommunityCategory category,
            Instant createdAt) {
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(createdAt, "createdAt");
        return new CommunityQuestion(
                authorId,
                requireText(title, TITLE_MIN_LENGTH, TITLE_MAX_LENGTH, "title"),
                requireText(body, BODY_MIN_LENGTH, BODY_MAX_LENGTH, "body"),
                category,
                createdAt);
    }

    /**
     * Rewrites the parts of a question its author may change.
     *
     * <p>Only the title, the body and the category. Neither the author, nor the
     * timestamps, nor the status, nor the accepted answer is reachable from here, so an
     * edit can never be used to reassign authorship, republish withdrawn content or
     * mark a question solved.</p>
     *
     * @throws BusinessConflictException when the question is no longer publicly visible,
     *         because an edit to withdrawn or hidden content would be invisible to
     *         everyone but its author
     */
    public void edit(String title, String body, CommunityCategory category, Instant at) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(at, "at");
        if (status != CommunityContentStatus.VISIBLE) {
            throw new BusinessConflictException(
                    "This question can no longer be edited because it is not publicly visible.");
        }
        this.title = requireText(title, TITLE_MIN_LENGTH, TITLE_MAX_LENGTH, "title");
        this.body = requireText(body, BODY_MIN_LENGTH, BODY_MAX_LENGTH, "body");
        this.category = category;
        this.updatedAt = at;
    }

    /**
     * Takes the question out of public view at its author's request.
     *
     * <p>Idempotent: withdrawing an already-withdrawn question is a no-op rather than a
     * conflict, so a double-submitted form cannot produce an error page for an action
     * that already had the intended effect.</p>
     *
     * <p>The row is retained. The brief forbids physically deleting user content, and
     * retention is what lets a moderation decision be reviewed and an author still see
     * their own withdrawn question in "my questions".</p>
     *
     * @throws BusinessConflictException when a moderator has hidden the question. An
     *         author may take their own content down, but not silently overwrite the
     *         record of a moderation decision; the two states mean different things and
     *         only an administrator may move a question out of {@code HIDDEN}.
     */
    public void withdraw(Instant at) {
        Objects.requireNonNull(at, "at");
        if (status == CommunityContentStatus.WITHDRAWN) {
            return;
        }
        if (status != CommunityContentStatus.VISIBLE) {
            throw new BusinessConflictException(
                    "This question is not publicly visible, so it cannot be withdrawn.");
        }
        status = CommunityContentStatus.WITHDRAWN;
        updatedAt = at;
    }

    /**
     * Takes the question out of public view at a moderator's decision.
     *
     * <h2>What hiding a question does not touch</h2>
     *
     * <p>Its answers, and its accepted answer. The thread is retained exactly as it
     * was: a hidden question keeps every answer, visible ones included, because the
     * alternative - deleting or hiding replies to punish the person who asked - would
     * destroy content written by people who did nothing, and the brief forbids
     * physically deleting user content for the same reason. The acceptance is left
     * alone as well; a question that comes back from being hidden comes back with the
     * answer its author had chosen still marked, because nobody decided otherwise. The
     * one case where a moderation decision does clear an acceptance is hiding the
     * <em>accepted answer itself</em>, which is the answer entity's concern and the
     * service's job to do in the same transaction.</p>
     *
     * <p>Only {@code VISIBLE} content can be hidden; see
     * {@link CommunityAnswer#hide} for why the other two states are left alone.</p>
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
     * Returns a hidden question to public view.
     *
     * <p>Only {@code HIDDEN} content can be restored: a question its author withdrew
     * stays withdrawn, so reversing a moderation decision never also reverses the
     * author's own. Its answers are untouched here, and an answer that was visible
     * before the question was hidden is visible again after it is restored, because
     * nothing ever changed it.</p>
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

    /** Whether a public list, search or count may return this question. */
    public boolean isPubliclyVisible() {
        return status != null && status.isPubliclyVisible();
    }

    /** Whether an answer has been accepted. Derived; see the class comment. */
    public boolean isSolved() {
        return acceptedAnswerId != null;
    }

    private static String requireText(String value, int minimumLength, int maximumLength, String fieldName) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.length() < minimumLength || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " must contain between "
                    + minimumLength + " and " + maximumLength + " characters");
        }
        return normalized;
    }

    public Long getId() {
        return id;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public CommunityCategory getCategory() {
        return category;
    }

    public CommunityContentStatus getStatus() {
        return status;
    }

    public Long getAcceptedAnswerId() {
        return acceptedAnswerId;
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

    /** Whether the question has been changed since it was posted. */
    public boolean isEdited() {
        return createdAt != null && updatedAt != null && updatedAt.isAfter(createdAt);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CommunityQuestion question) || id == null) {
            return false;
        }
        return id.equals(question.id);
    }

    @Override
    public int hashCode() {
        return CommunityQuestion.class.hashCode();
    }
}
