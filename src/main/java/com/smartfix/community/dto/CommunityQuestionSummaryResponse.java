package com.smartfix.community.dto;

import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityContentStatus;

import java.time.Instant;

/**
 * One row of the community list, or of "my questions".
 *
 * <p>Carries {@code status} even though the public list only ever holds
 * {@link CommunityContentStatus#VISIBLE} rows: "my questions" deliberately includes an
 * author's own withdrawn and hidden questions, and the page has to say which is which
 * rather than show them looking published.</p>
 *
 * <p>There is no answer count here. Producing one for a page of rows would be a count
 * query per row, and answering is a later phase, so every row in this delivery would pay
 * that cost to print a zero. The detail page counts once, where the number is worth
 * showing.</p>
 *
 * @param id        question id, used to build the detail link
 * @param title     the question's title, already trimmed
 * @param excerpt   the opening of the body, shortened for the list
 * @param category  the topic the author filed it under
 * @param authorId  the account that asked; the page renders it as {@code Account #n}
 * @param status    publication state
 * @param solved    whether an answer has been accepted; derived, never stored
 * @param createdAt when it was posted
 * @param updatedAt when it was last changed
 * @param edited    whether anything has changed it since it was posted
 */
public record CommunityQuestionSummaryResponse(
        Long id,
        String title,
        String excerpt,
        CommunityCategory category,
        Long authorId,
        CommunityContentStatus status,
        boolean solved,
        Instant createdAt,
        Instant updatedAt,
        boolean edited
) {
}
