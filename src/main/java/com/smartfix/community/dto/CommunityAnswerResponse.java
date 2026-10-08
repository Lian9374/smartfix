package com.smartfix.community.dto;

import com.smartfix.community.domain.CommunityContentStatus;

import java.time.Instant;

/**
 * One answer as the detail page renders it.
 *
 * <p>{@code body} is {@code null} whenever the answer is not publicly visible, and the
 * template prints a placeholder instead of the text. That is why the body is nullable
 * here rather than the answer being filtered out: a withdrawn answer has to disappear for
 * everyone except its author and an administrator, and a "vanished" thread reads as a
 * bug. A null body cannot leak the text, whereas a leftover field might.</p>
 *
 * @param id            answer id
 * @param questionId    the question it answers
 * @param authorId      the account that wrote it
 * @param body          the text, or {@code null} when it is not publicly visible
 * @param status        publication state
 * @param accepted      whether this is the question's accepted answer
 * @param createdAt     when it was posted
 * @param updatedAt     when it was last changed
 * @param edited        whether anything has changed it since it was posted
 */
public record CommunityAnswerResponse(
        Long id,
        Long questionId,
        Long authorId,
        String body,
        CommunityContentStatus status,
        boolean accepted,
        Instant createdAt,
        Instant updatedAt,
        boolean edited
) {
}
