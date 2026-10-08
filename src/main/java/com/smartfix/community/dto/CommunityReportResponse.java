package com.smartfix.community.dto;

import com.smartfix.community.domain.CommunityContentStatus;
import com.smartfix.community.domain.CommunityContentType;
import com.smartfix.community.domain.CommunityReportReason;
import com.smartfix.community.domain.CommunityReportStatus;

import java.time.Instant;

/**
 * One row of the moderation queue: the report, and what it is about.
 *
 * <h2>The reported content travels with the report</h2>
 *
 * <p>{@code targetBody} carries the text a moderator is being asked to judge, in
 * full rather than as an excerpt. The public detail page cannot serve that purpose:
 * it answers 404 to anybody but the author once content is hidden, and a moderator who
 * had to hide something before reading it would be deciding blind. The queue is the
 * moderation view of that content, and it is the place the visibility table in plan
 * section 6.8 means when it says an administrator can see hidden content.</p>
 *
 * <p>Nothing about the content is withheld from this response on account of status -
 * that is the point of it - but the response carries no author name, only an account
 * id, exactly as the public pages do, because the moderation decision is about content
 * and not about who wrote it.</p>
 *
 * <h2>Nulls, and what each one means</h2>
 *
 * <ul>
 *   <li>{@code questionTitle}, {@code questionStatus}, {@code questionId} - the thread
 *       the target belongs to. For a question report these describe the target itself.
 *       For an answer report they are read from the answer's parent. All three are null
 *       when the target row cannot be found, which the foreign keys make unreachable in
 *       practice; the queue renders such a row rather than failing, because a moderator
 *       still needs to be able to clear a report whose content is gone.</li>
 *   <li>{@code targetBody}, {@code targetStatus}, {@code targetAuthorId} - null
 *       together, under the same condition.</li>
 *   <li>{@code detail}, {@code handledByUserId}, {@code handledAt},
 *       {@code resolutionNote} - the reporter's note is optional by design, and the
 *       other three are null exactly while the report is {@code OPEN}.</li>
 * </ul>
 *
 * @param id                 the report
 * @param targetType         whether a question or an answer was reported
 * @param targetId           the reported content
 * @param questionId         the thread the target lives in; equal to {@code targetId}
 *                           when a question was reported
 * @param questionTitle      that thread's title, or null when it cannot be read
 * @param questionStatus     that thread's publication state, or null likewise
 * @param targetBody         the reported text, in full, or null when gone
 * @param targetStatus       the reported content's own state, or null when gone
 * @param targetAuthorId     who wrote the reported content, or null when gone
 * @param reason             why it was reported
 * @param detail             the reporter's optional note
 * @param reporterId         who reported it
 * @param status             {@code OPEN}, {@code ACTIONED} or {@code DISMISSED}
 * @param createdAt          when it was reported
 * @param handledByUserId    the moderator who settled it, or null while open
 * @param handledAt          when it was settled, or null while open
 * @param resolutionNote     that moderator's optional note
 */
public record CommunityReportResponse(
        Long id,
        CommunityContentType targetType,
        Long targetId,
        Long questionId,
        String questionTitle,
        CommunityContentStatus questionStatus,
        String targetBody,
        CommunityContentStatus targetStatus,
        Long targetAuthorId,
        CommunityReportReason reason,
        String detail,
        Long reporterId,
        CommunityReportStatus status,
        Instant createdAt,
        Long handledByUserId,
        Instant handledAt,
        String resolutionNote) {

    /**
     * Whether the thread page would render for a moderator who followed a link to it.
     *
     * <p>The public detail page shows a non-public question only to its author, and an
     * administrator is not the author, so a link to a hidden or withdrawn question's
     * thread leads to a 404. The queue therefore offers the link only when this is
     * true, and shows the content inline either way - a destination that leads nowhere
     * is worse than an absent one, which is the rule the page shell states.</p>
     */
    public boolean isThreadPubliclyVisible() {
        return questionStatus != null && questionStatus.isPubliclyVisible();
    }

    /** Whether the reported content is currently hidden by moderation. */
    public boolean isTargetHidden() {
        return targetStatus == CommunityContentStatus.HIDDEN;
    }

    /** Whether the reported content is currently in public view. */
    public boolean isTargetVisible() {
        return targetStatus == CommunityContentStatus.VISIBLE;
    }

    /** Whether the moderator may still decide this report. */
    public boolean isOpen() {
        return status == CommunityReportStatus.OPEN;
    }
}
