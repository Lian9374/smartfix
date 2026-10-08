package com.smartfix.community.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.community.config.CommunityProperties;
import com.smartfix.community.domain.CommunityQuestion;
import com.smartfix.community.dto.QuestionFormCommand;
import com.smartfix.community.repository.CommunityQuestionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Write side of the community board: asking, editing and withdrawing a question.
 *
 * <h2>Authorship</h2>
 *
 * <p>Every method takes {@code actorUserId} from the caller, which takes it from the
 * authenticated principal. No method accepts an author from a request parameter, because
 * {@link QuestionFormCommand} has no such field - plan section 5.3 makes identity an
 * invariant of the session, not an input.</p>
 *
 * <h2>Ownership is checked here, not on the page</h2>
 *
 * <p>Editing or withdrawing someone else's question answers 404, not 403. The lookup
 * filters by author in the query itself ({@code findByIdAndAuthorId}), so there is no
 * branch in which the question is loaded and then judged: a query that cannot return
 * another author's row cannot accidentally act on one.</p>
 */
@Service
@Transactional
public class CommunityQuestionService {

    private final CommunityQuestionRepository questions;
    private final CommunityAccessGuard access;
    private final CommunityProperties properties;
    private final Clock clock;

    public CommunityQuestionService(
            CommunityQuestionRepository questions,
            CommunityAccessGuard access,
            CommunityProperties properties,
            Clock clock) {
        this.questions = questions;
        this.access = access;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Posts a new question on behalf of the authenticated account.
     *
     * @return the new question's id
     * @throws InputValidationException when the same text was posted moments ago, or the
     *         author has posted as often as the configured limit allows
     */
    public Long ask(QuestionFormCommand command, Long actorUserId) {
        access.requireActiveUser(actorUserId);
        Instant now = clock.instant();
        rejectRepetition(actorUserId, command, now);
        CommunityQuestion question = CommunityQuestion.ask(
                actorUserId,
                command.getTitle(),
                command.getBody(),
                command.getCategory(),
                now);
        return questions.save(question).getId();
    }

    /**
     * Saves an edit to a question the caller wrote.
     *
     * <p>Only the title, the body and the category can change. The command carries
     * nothing else, so an edit cannot be used to reassign authorship, to republish
     * withdrawn content, or to mark the question solved.</p>
     *
     * @throws ResourceNotFoundException when no such question exists or the caller is not
     *         its author - the two are one answer on purpose
     * @throws BusinessConflictException when the question is hidden or withdrawn, because
     *         editing content nobody can see has no visible effect
     */
    public void edit(Long questionId, QuestionFormCommand command, Long actorUserId) {
        access.requireActiveUser(actorUserId);
        CommunityQuestion question = requireOwnQuestion(questionId, actorUserId);
        question.edit(command.getTitle(), command.getBody(), command.getCategory(), clock.instant());
    }

    /**
     * Takes a question the caller wrote out of public view.
     *
     * <p>Idempotent: withdrawing twice is a no-op rather than an error, so a double
     * submission cannot turn an action that already succeeded into an error page. The row
     * is kept - nothing in this module deletes user content.</p>
     *
     * @throws ResourceNotFoundException when no such question exists or the caller is not
     *         its author
     * @throws BusinessConflictException when a moderator has hidden it, so that an
     *         author's action cannot overwrite a moderation decision
     */
    public void withdraw(Long questionId, Long actorUserId) {
        access.requireActiveUser(actorUserId);
        CommunityQuestion question = requireOwnQuestion(questionId, actorUserId);
        question.withdraw(clock.instant());
    }

    private CommunityQuestion requireOwnQuestion(Long questionId, Long actorUserId) {
        if (questionId == null) {
            throw new ResourceNotFoundException("Community content not found.");
        }
        return questions.findByIdAndAuthorId(questionId, actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Community content not found."));
    }

    /**
     * Applies the two posting guards: no identical repost inside a short window, and no
     * more than a configured number of questions per window.
     *
     * <h3>Status of these guards</h3>
     *
     * <p>Both are driven by {@link CommunityProperties}, and neither value is a team
     * decision - plan section 29 leaves D-15 open and the plan states two different
     * numbers. See the class comment on {@code CommunityProperties}. Guarding can be
     * turned off entirely by configuration, which is the plan's option two.</p>
     *
     * <h3>What the guards do and do not guarantee</h3>
     *
     * <p>Each guard is a count followed by an insert, and the two are not atomic with
     * respect to each other. Two submissions from the same author that overlap in time
     * can therefore both read a count below the limit and both insert - the window is a
     * rate limit in the ordinary sense, not an exactly-once or at-most-N guarantee. What
     * it does bound is the ordinary case: a double-clicked button, a refreshed POST, or a
     * script posting in a loop one request at a time.</p>
     *
     * <p>Making it exact would mean serialising per author - taking a row lock on the
     * account inside the same transaction, or a unique constraint over a time bucket -
     * and both are more machinery than D-15 justifies while it is undecided. Stating the
     * limit honestly matters more than appearing to have one that is stronger than it is.</p>
     */
    private void rejectRepetition(Long authorId, QuestionFormCommand command, Instant now) {
        CommunityProperties.Posting posting = properties.getPosting();
        if (!posting.isDuplicateDetectionEnabled()) {
            return;
        }
        if (!posting.getDuplicateWindow().isZero()) {
            long identical = questions.countByAuthorIdAndTitleIgnoreCaseAndBodyIgnoreCaseAndCreatedAtGreaterThanEqual(
                    authorId, command.getTitle(), command.getBody(), now.minus(posting.getDuplicateWindow()));
            if (identical > 0) {
                throw new InputValidationException(
                        "You posted this question a moment ago. Please wait before posting it again.");
            }
        }
        long recent = questions.countByAuthorIdAndCreatedAtGreaterThanEqual(
                authorId, now.minus(posting.getRateLimitWindow()));
        if (recent >= posting.getMaxPerRateLimitWindow()) {
            throw new InputValidationException(
                    "You have reached the limit of " + posting.getMaxPerRateLimitWindow()
                            + " questions in this period. Please try again later.");
        }
    }
}
