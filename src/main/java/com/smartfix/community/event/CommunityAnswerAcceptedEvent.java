package com.smartfix.community.event;

import java.time.Instant;

/**
 * A question's author accepted an answer.
 *
 * <h2>Published only when the acceptance actually happened</h2>
 *
 * <p>The accept flow decides the winner with a single conditional {@code UPDATE} whose
 * affected-row count is the result. This event is published on a count of one and never
 * on a count of zero, so a losing racer - or a second click on an already-accepted
 * question - produces a business conflict and no notification. Plan section 12.2 states
 * that coupling explicitly, and it is the reason the count is used rather than a
 * read-then-write: with a read-then-write there would be a window in which both callers
 * believed they had won and both would tell the answerer their answer was accepted.</p>
 *
 * <h2>Timing and payload</h2>
 *
 * <p>Published inside the business transaction; subscribers consume it with {@code
 * @TransactionalEventListener(phase = AFTER_COMMIT)}. Ids and a timestamp only, no
 * entities - see {@link CommunityAnswerCreatedEvent} for the reasoning the two events
 * share.</p>
 *
 * <p><strong>Field list frozen as F-6</strong> by plan section 29.1.</p>
 *
 * @param answerId        the accepted answer
 * @param questionId      the question it was accepted for
 * @param questionAuthorId the account that accepted it, and the actor of the change
 * @param answerAuthorId  the account to notify
 * @param occurredAt      when the acceptance was recorded
 */
public record CommunityAnswerAcceptedEvent(
        Long answerId,
        Long questionId,
        Long questionAuthorId,
        Long answerAuthorId,
        Instant occurredAt) {
}
