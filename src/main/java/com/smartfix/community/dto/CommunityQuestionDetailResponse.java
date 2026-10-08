package com.smartfix.community.dto;

import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityContentStatus;

import java.time.Instant;
import java.util.List;

/**
 * A question and its thread, as the detail page renders them.
 *
 * <p>{@code answers} is never {@code null}; an empty thread is an empty list, so the
 * template distinguishes "no answers yet" from a missing attribute without a null check
 * that could itself be forgotten.</p>
 *
 * @param id               question id
 * @param title            the title, already trimmed
 * @param body             the full text; the template prints it with {@code th:text}
 * @param category         the topic the author filed it under
 * @param authorId         the account that asked
 * @param status           publication state
 * @param solved           whether an answer has been accepted; derived, never stored
 * @param acceptedAnswerId the accepted answer's id, or {@code null} while the question is open
 * @param createdAt        when it was posted
 * @param updatedAt        when it was last changed
 * @param edited           whether anything has changed it since it was posted
 * @param answers          the thread, oldest first; withdrawn and hidden answers are present
 *                         with a {@code null} body so the page can show a placeholder
 */
public record CommunityQuestionDetailResponse(
        Long id,
        String title,
        String body,
        CommunityCategory category,
        Long authorId,
        CommunityContentStatus status,
        boolean solved,
        Long acceptedAnswerId,
        Instant createdAt,
        Instant updatedAt,
        boolean edited,
        List<CommunityAnswerResponse> answers
) {
}
