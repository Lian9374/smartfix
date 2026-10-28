package com.smartfix.community.event;

import java.time.Instant;

/**
 * An answer was posted to a question.
 *
 * <h2>Who publishes it, and when</h2>
 *
 * <p>Published by {@code CommunityAnswerService.post} from inside the business
 * transaction, once the answer row exists and before that transaction commits. The
 * consumer side is plan section 12.6: every subscriber uses {@code
 * @TransactionalEventListener(phase = AFTER_COMMIT)}, so a transaction that rolls back
 * after this point sends nothing, and a delivery failure after the commit does not roll
 * the answer back - the notification module owns its own retry (plan section 14.7).</p>
 *
 * <h2>Payload</h2>
 *
 * <p>Ids and one timestamp, per plan section 12.6: no JPA entity, no {@code User}, no
 * request object. An event carrying a managed entity would let a subscriber read - or
 * lazily load - state that has changed since the event was raised, which is precisely
 * what a notification is not allowed to do.</p>
 *
 * <p>The two author ids are both here because the notification this event exists for is
 * "somebody answered your question": {@code questionAuthorId} is who to tell, and
 * {@code answerAuthorId} is who to name. Carrying only one of them would force the
 * subscriber to look the other up, which is the dependency this payload avoids.</p>
 *
 * <p><strong>Field list frozen as F-5</strong> by plan section 29.1. Changing it is a
 * document-first change (plan section 28.3), not a quiet edit: the notification module
 * writes its listener against these names.</p>
 *
 * @param answerId        the new answer's id
 * @param questionId      the question it answers
 * @param questionAuthorId the account that asked, and the one to notify
 * @param answerAuthorId  the account that answered
 * @param occurredAt      when the answer was posted, from the service clock
 */
public record CommunityAnswerCreatedEvent(
        Long answerId,
        Long questionId,
        Long questionAuthorId,
        Long answerAuthorId,
        Instant occurredAt) {
}
