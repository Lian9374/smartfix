package com.smartfix.community.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.community.config.CommunityProperties;
import com.smartfix.community.domain.CommunityAnswer;
import com.smartfix.community.domain.CommunityContentStatus;
import com.smartfix.community.domain.CommunityContentType;
import com.smartfix.community.domain.CommunityQuestion;
import com.smartfix.community.dto.AnswerFormCommand;
import com.smartfix.community.event.CommunityAnswerAcceptedEvent;
import com.smartfix.community.event.CommunityAnswerCreatedEvent;
import com.smartfix.community.event.CommunityContentHiddenEvent;
import com.smartfix.community.repository.CommunityAnswerRepository;
import com.smartfix.community.repository.CommunityQuestionRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Write side of the community board for answers: posting, editing, withdrawing, accepting
 * and un-accepting.
 *
 * <h2>Authorisation</h2>
 *
 * <p>As in {@code CommunityQuestionService}, {@code actorUserId} always comes from the
 * caller's authenticated principal and never from a request field. The accept routes do
 * carry ids in the path, but those name <em>content</em>, not identity: the actor is still
 * the session's account, so a crafted URL can only point at an answer the actor is not
 * allowed to accept - which is answered 404, not accepted.</p>
 *
 * <h2>Every refusal is a 404 or a conflict, and never a 403</h2>
 *
 * <p>Editing or withdrawing somebody else's answer, and accepting an answer to somebody
 * else's question, all answer {@link ResourceNotFoundException}. A 403 would confirm that
 * the content exists, which turns the endpoint into a way of enumerating other people's
 * ids. Plan section 13.4 and the sprint 3 brief both require the 404.</p>
 *
 * <h2>One lock, taken first, and what it is for</h2>
 *
 * <p>Accepting, un-accepting and withdrawing all read state from two rows - the answer's
 * status and the question's accepted answer - and then write. Two of those operations can
 * interleave so that a check which saw a {@code VISIBLE} answer is followed by somebody
 * else withdrawing it, and the acceptance commits anyway, leaving a question pointing at
 * withdrawn content. The composite foreign key does not catch that: it proves the answer
 * belongs to the question, not that it is still visible.</p>
 *
 * <p>So each of the three takes a write lock on the <em>question</em> row
 * ({@code findByIdForUpdate}) before it reads or writes anything, and then does its
 * reads. The second transaction blocks until the first commits, and by the time it
 * proceeds its reads see the committed result. Because the question row is the only lock
 * any of them takes, and it is always taken first, there is no order for them to get
 * wrong and no cycle to deadlock on.</p>
 *
 * <p>The lock is a serialisation device, not the invariant. The invariant - at most one
 * accepted answer per question - is enforced separately by a conditional
 * {@code UPDATE} whose affected-row count decides the outcome; see
 * {@code CommunityQuestionRepository.acceptAnswerIfOpen}. Keeping both means the
 * invariant does not depend on a future caller remembering to lock, and the cross-row
 * hazard does not depend on a single statement being clever enough.</p>
 *
 * <h2>Events</h2>
 *
 * <p>Published inside the business transaction. The notification module consumes
 * answers, acceptance and moderation hides with AFTER_COMMIT listeners; rollback
 * sends nothing. See CommunityNotificationListener and ADR-003.</p>
 */
@Service
@Transactional
public class CommunityAnswerService {

    /** The single refusal every ownership failure shares. */
    private static final String NOT_FOUND = "Community content not found.";

    /** The single refusal for content that exists but is not public. */
    private static final String NOT_PUBLIC = "This content is not publicly visible.";

    /** Raised when the conditional accept update affects no row. Plan section 12.2. */
    private static final String ALREADY_ACCEPTED =
            "This question already has an accepted answer.";

    /** Raised when the question's author tries to accept an answer they wrote. See {@link #accept}. */
    private static final String SELF_ACCEPTANCE = "You cannot accept your own answer.";

    private final CommunityQuestionRepository questions;
    private final CommunityAnswerRepository answers;
    private final CommunityAccessGuard access;
    private final CommunityProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CommunityAnswerService(
            CommunityQuestionRepository questions,
            CommunityAnswerRepository answers,
            CommunityAccessGuard access,
            CommunityProperties properties,
            ApplicationEventPublisher events,
            Clock clock) {
        this.questions = questions;
        this.answers = answers;
        this.access = access;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Posts an answer to a question, on behalf of the authenticated account.
     *
     * <p>Any ACTIVE role may answer - requester, technician or administrator - because the
     * board is one shared place and the route matrix puts no role condition on it. An
     * administrator answering here is an ordinary member of the board: there is no
     * elevated variant of this method, and nothing in the accept flow treats them
     * differently either.</p>
     *
     * <p>The question must be publicly visible. Its author may read their own withdrawn
     * question on the detail page, but that page offers no answer form for it and this
     * method would refuse one anyway - a reply nobody but the question's author can read
     * is not what the board is for.</p>
     *
     * <p>No lock is taken. A question withdrawn concurrently with this call can leave an
     * answer attached to a question that is not public, which is harmless: the answer is
     * unreachable through any public read, the question's author still sees the thread,
     * and nothing here depends on the answer being visible. Taking the lock would
     * serialise every answer against every withdrawal to prevent a state that costs
     * nothing.</p>
     *
     * @return the new answer's id
     * @throws ResourceNotFoundException when there is no such question, or it is not public
     * @throws InputValidationException when the author has answered as often in the
     *         configured window as the posting guard allows
     */
    public Long post(Long questionId, AnswerFormCommand command, Long actorUserId) {
        access.requireActiveUser(actorUserId);
        Instant now = clock.instant();
        rejectFlood(actorUserId, now);

        CommunityQuestion question = requirePublicQuestion(questionId);
        CommunityAnswer answer = answers.save(CommunityAnswer.post(
                question.getId(), actorUserId, command.getBody(), now));

        // In-transaction, after the row exists and before the commit. A rollback after
        // this point leaves no event for an answer that was never stored.
        events.publishEvent(new CommunityAnswerCreatedEvent(
                answer.getId(),
                question.getId(),
                question.getAuthorId(),
                actorUserId,
                now));
        return answer.getId();
    }

    /**
     * Saves an edit to an answer the caller wrote.
     *
     * <p>Only the body can change. The command carries nothing else, so an edit cannot
     * reassign authorship, move the answer to another question, or republish withdrawn
     * content.</p>
     *
     * <p>The parent question's visibility is deliberately not re-checked. Whether an
     * answer may be edited is a property of the answer, and the answer's own state is what
     * the entity checks; adding the parent would mean two places with an opinion about
     * when an edit is allowed, and the stricter one would silently win.</p>
     *
     * @throws ResourceNotFoundException when there is no such answer or the caller did not
     *         write it - one answer on purpose
     * @throws BusinessConflictException when the answer is no longer publicly visible,
     *         because the edit would have no visible effect
     */
    public void edit(Long answerId, AnswerFormCommand command, Long actorUserId) {
        access.requireActiveUser(actorUserId);
        CommunityAnswer answer = requireOwnAnswer(answerId, actorUserId);
        answer.edit(command.getBody(), clock.instant());
    }

    /**
     * Takes an answer the caller wrote out of public view.
     *
     * <p>If the answer was the question's accepted answer, the question is opened again in
     * the same transaction. The alternative - leaving an accepted answer marked accepted
     * once it is withdrawn - would leave a question reading "Solved" with no readable
     * answer, which is the state the derived-solved-state rule exists to prevent. The
     * clearing is a conditional statement of its own, so it cannot clear an acceptance
     * that a concurrent operation has already replaced.</p>
     *
     * <p>Idempotent: withdrawing an already-withdrawn answer changes nothing and raises no
     * event, so a double-submitted form cannot turn a successful action into an error
     * page. The row is retained, like every other withdrawal here.</p>
     *
     * @throws ResourceNotFoundException when there is no such answer or the caller did not
     *         write it
     */
    public void withdraw(Long answerId, Long actorUserId) {
        access.requireActiveUser(actorUserId);
        Instant now = clock.instant();

        // The answer id is what the route names, so the question to lock is discovered
        // first - through a scalar query, so that the answer itself is not loaded into the
        // persistence context and the read below really happens after the lock.
        Long questionId = answers.findQuestionIdOfOwnAnswer(answerId, actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        CommunityQuestion question = lockQuestion(questionId);
        CommunityAnswer answer = answers.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));

        boolean changed = answer.getStatus() != CommunityContentStatus.WITHDRAWN;
        answer.withdraw(now);

        // Cleared whenever the question still points at this answer, whether or not the
        // status changed in this call: an already-withdrawn answer that is somehow still
        // the accepted one must not stay that way.
        if (answerId.equals(question.getAcceptedAnswerId())) {
            questions.clearAcceptanceIfPresent(questionId, now);
        }

        if (changed) {
            events.publishEvent(new CommunityContentHiddenEvent(
                    answerId,
                    CommunityContentType.ANSWER,
                    questionId,
                    actorUserId,
                    actorUserId,
                    CommunityContentStatus.WITHDRAWN,
                    now));
        }
    }

    /**
     * Accepts one of a question's answers, on behalf of the question's author.
     *
     * <h2>The rules, in the order plan section 12.2 gives them</h2>
     *
     * <ol>
     *   <li>the caller wrote the question, or 404;</li>
     *   <li>the question is publicly visible, or a conflict;</li>
     *   <li>the answer is an answer to <em>this</em> question, or 404 - which is what an
     *       answer id from another question in the URL runs into;</li>
     *   <li>the answer is publicly visible, or a conflict;</li>
     *   <li>the answer was not written by the caller;</li>
     *   <li>then the conditional update, whose affected-row count decides between a
     *       recorded acceptance and a conflict.</li>
     * </ol>
     *
     * <p>Rule 3 precedes rule 4 on purpose. An answer id belonging to another question
     * must answer 404, and a hidden answer to a foreign question would otherwise answer a
     * conflict - which tells the caller that the id exists.</p>
     *
     * <p>Rule 5 is fixed by ADR-003: a question author cannot accept their own answer.</p>
     *
     * @throws ResourceNotFoundException when the caller did not write the question, or the
     *         answer is not an answer to it
     * @throws BusinessConflictException when the question or the answer is no longer
     *         visible, when the caller wrote the answer, or when the question already has
     *         an accepted answer
     */
    public void accept(Long questionId, Long answerId, Long actorUserId) {
        access.requireActiveUser(actorUserId);
        Instant now = clock.instant();
        if (questionId == null || answerId == null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }

        CommunityQuestion question = lockQuestion(questionId);
        if (!question.getAuthorId().equals(actorUserId)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        if (!question.isPubliclyVisible()) {
            throw new BusinessConflictException(NOT_PUBLIC);
        }

        CommunityAnswer answer = answers.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (!questionId.equals(answer.getQuestionId())) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        if (!answer.isPubliclyVisible()) {
            throw new BusinessConflictException(NOT_PUBLIC);
        }
        if (answer.getAuthorId().equals(actorUserId)) {
            throw new BusinessConflictException(SELF_ACCEPTANCE);
        }

        int affected = questions.acceptAnswerIfOpen(questionId, answerId, now);
        if (affected == 0) {
            throw new BusinessConflictException(ALREADY_ACCEPTED);
        }

        events.publishEvent(new CommunityAnswerAcceptedEvent(
                answerId,
                questionId,
                actorUserId,
                answer.getAuthorId(),
                now));
    }

    /**
     * Removes the accepted answer from a question the caller wrote, leaving it open.
     *
     * <p>Idempotent by construction: the clearing statement affects no row when there is
     * nothing to clear, and that is the state the caller asked for. There is no event -
     * plan section 12.2 gives this method none, and nothing downstream is told that a
     * question went from solved to open.</p>
     *
     * <p>It does not restore anything. Un-accepting an answer whose content was later
     * taken out of view does not bring the content back, and restoring content does not
     * bring an acceptance back either: acceptance is set by exactly one statement and
     * cleared by exactly one, and no other operation in the module writes the column. A
     * question that was solved, had its acceptance removed and its answer restored is an
     * open question with a visible answer, which is what each of those three actions
     * individually said.</p>
     *
     * @throws ResourceNotFoundException when the caller did not write the question
     */
    public void removeAcceptance(Long questionId, Long actorUserId) {
        access.requireActiveUser(actorUserId);
        CommunityQuestion question = lockQuestion(questionId);
        if (!question.getAuthorId().equals(actorUserId)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        questions.clearAcceptanceIfPresent(questionId, clock.instant());
    }

    private CommunityQuestion lockQuestion(Long questionId) {
        if (questionId == null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return questions.findByIdForUpdate(questionId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private CommunityQuestion requirePublicQuestion(Long questionId) {
        if (questionId == null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return questions.findById(questionId)
                .filter(CommunityQuestion::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private CommunityAnswer requireOwnAnswer(Long answerId, Long actorUserId) {
        if (answerId == null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return answers.findByIdAndAuthorId(answerId, actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /**
     * The answer half of the posting guard: no more than a configured number of answers
     * per author per window.
     *
     * <p>It reuses the question guard's window and cap rather than introducing numbers of
     * its own. Plan section 12.2 lists a rate-limit conflict for posting an answer but
     * gives no figure, and section 29 leaves D-15 open with two different values in the
     * plan itself - so inventing an answer-specific pair would be adding a decision to a
     * question the team has not answered. The switch that turns the question guards off
     * turns this one off too, which is what makes "turn the posting guards off" mean the
     * same thing for both kinds of content.</p>
     *
     * <p>As with the question guard, this is a count followed by an insert and the two are
     * not atomic with respect to each other, so it bounds the ordinary case - a
     * double-clicked button, a script posting one request at a time - and not a pair of
     * simultaneous submissions.</p>
     */
    private void rejectFlood(Long authorId, Instant now) {
        CommunityProperties.Posting posting = properties.getPosting();
        if (!posting.isDuplicateDetectionEnabled()) {
            return;
        }
        long recent = answers.countByAuthorIdAndCreatedAtGreaterThanEqual(
                authorId, now.minus(posting.getRateLimitWindow()));
        if (recent >= posting.getMaxPerRateLimitWindow()) {
            throw new InputValidationException(
                    "You have reached the limit of " + posting.getMaxPerRateLimitWindow()
                            + " answers in this period. Please try again later.");
        }
    }
}
