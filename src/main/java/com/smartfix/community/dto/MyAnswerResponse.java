package com.smartfix.community.dto;

import com.smartfix.community.domain.CommunityContentStatus;

import java.time.Instant;

/**
 * One row of "my answers": an answer the signed-in account wrote, and as much of its
 * question as that account is allowed to see.
 *
 * <h2>Why the question's title is nullable</h2>
 *
 * <p>An answer almost always outlives its question's publicity. Somebody answers a
 * question, the asker withdraws it, a moderator hides it, and the answer is still the
 * answerer's own content - it belongs in this list. But the question it replies to is no
 * longer readable by the person reading this page, and a list that named it anyway would
 * be an answer route around the rule the detail page enforces.</p>
 *
 * <p>So the title is present exactly when the reader may read that question, which is the
 * same predicate the detail page uses: the question is {@code VISIBLE}, or the reader is
 * its author. When it is {@code null} the page prints one neutral line - not "withdrawn",
 * not "hidden" - because the distinction between those two is a moderation fact that
 * this list has no reason to disclose.</p>
 *
 * <p>{@code body} is the reader's <em>own</em> text, so it is always present, including
 * when the answer has been withdrawn or hidden: plan section 6.7 (R14) lets an author see
 * their own non-public content. Nothing here can leak a question's body, its title, or
 * another account's text - the row is selected by author id.</p>
 *
 * @param id            the answer's id
 * @param questionId    the question it answers; safe to carry, because it is an id this
 *                      account already holds through its own answer
 * @param questionTitle the question's title, or {@code null} when the reader may not read
 *                      that question
 * @param body          the answer's own text, as the author wrote it
 * @param status        the answer's publication state
 * @param createdAt     when it was posted
 * @param updatedAt     when it was last changed
 * @param edited        whether anything has changed it since it was posted
 */
public record MyAnswerResponse(
        Long id,
        Long questionId,
        String questionTitle,
        String body,
        CommunityContentStatus status,
        Instant createdAt,
        Instant updatedAt,
        boolean edited
) {
}
