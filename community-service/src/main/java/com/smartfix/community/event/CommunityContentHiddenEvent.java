package com.smartfix.community.event;

import com.smartfix.community.domain.CommunityContentStatus;
import com.smartfix.community.domain.CommunityContentType;

import java.time.Instant;

/**
 * Community content stopped being publicly visible.
 *
 * <h2>One event for every way that happens</h2>
 *
 * <p>Plan section 9.5.4 defines exactly three community events, and this is the third.
 * Withdrawal by an author, hiding by moderation, and the restoring half of a hide all
 * travel as this one type: plan section 12.2 raises it from {@code withdrawAnswer}, and
 * section 12.3 from {@code hideAnswer}, {@code restoreAnswer}, {@code hideQuestion},
 * {@code restoreQuestion} and a report resolved with {@code hideContent}. There is no
 * separate withdrawn-event and no restored-event, so {@link #resultingStatus} is what
 * says which way the content moved.</p>
 *
 * <h2>Why it carries the actor as well as the author</h2>
 *
 * <p>They are the same account when an author withdraws their own answer and different
 * accounts when a moderator hides one, and the audit subscriber (plan section 12.6) has
 * to record the second case as somebody acting on somebody else's content. Collapsing
 * them into one field would make that distinction unrecoverable.</p>
 *
 * <h2>Deliberately absent</h2>
 *
 * <p>No reason or note field. The moderation note belongs to the report that led to the
 * decision, not to the content, and a subscriber that needs it has the report id through
 * the report's own module. A nullable field that one publisher can never fill would look
 * like part of the contract while carrying nothing - and the field list here is frozen
 * as F-7 by plan section 29.1, so an unused field could not be removed quietly
 * afterwards.</p>
 *
 * <p>No body, no title, nothing copied from the content. The content is not deleted - it
 * is retained and readable to its author and to moderation (plan section 6.8) - so a
 * subscriber that genuinely needs it can ask for it by id. Copying it into an event
 * would put withdrawn text into a module that has no visibility rule of its own.</p>
 *
 * <p><strong>Field list frozen as F-7</strong> by plan section 29.1.</p>
 *
 * @param contentId       the question's or the answer's id, named by {@code contentType}
 * @param contentType     which of the two {@code contentId} is
 * @param questionId      the question the content belongs to; equal to {@code contentId}
 *                        when the content is itself a question, and the parent otherwise
 * @param authorId        the account whose content it is
 * @param actorUserId     the account that took it out of view - the author, or a moderator
 * @param resultingStatus {@code WITHDRAWN} or {@code HIDDEN}, or {@code VISIBLE} when a
 *                        hidden item was restored
 * @param occurredAt      when the change was recorded
 */
public record CommunityContentHiddenEvent(
        Long contentId,
        CommunityContentType contentType,
        Long questionId,
        Long authorId,
        Long actorUserId,
        CommunityContentStatus resultingStatus,
        Instant occurredAt) {
}
