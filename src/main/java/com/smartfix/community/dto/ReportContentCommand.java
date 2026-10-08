package com.smartfix.community.dto;

import com.smartfix.community.domain.CommunityReport;
import com.smartfix.community.domain.CommunityReportReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What a browser may submit when reporting a question or an answer.
 *
 * <h2>What is deliberately absent</h2>
 *
 * <p>{@code reporterId}, and the id of the thing being reported. The reporter is the
 * authenticated account, and the target comes from the route's path variable - so a
 * forged {@code reporterId} field has nothing to bind to, and a report cannot be filed
 * in somebody else's name or against content the URL does not name.</p>
 *
 * <h2>Why the note is optional and the reason is not</h2>
 *
 * <p>The reason is what the queue sorts and counts by, so it is required and comes
 * from a closed set. The note is where a reporter explains themselves, and requiring
 * one would make reporting a chore for the obvious cases while producing, for the
 * rest, a field filled with "n/a".</p>
 *
 * <p>The setter trims, for the reason {@link AnswerFormCommand} documents: {@code @Size}
 * measures what the setter stored, so trimming there is what makes this annotation, the
 * entity's own check and {@code V19}'s {@code chk_community_reports_detail} agree. A
 * note that is blank after trimming is stored as no note at all.</p>
 */
public class ReportContentCommand {

    @NotNull(message = "Choose a reason for the report.")
    private CommunityReportReason reason;

    @Size(max = CommunityReport.DETAIL_MAX_LENGTH,
            message = "A note must be at most " + CommunityReport.DETAIL_MAX_LENGTH
                    + " characters after trimming.")
    private String detail;

    public ReportContentCommand() {
        // form binding
    }

    public CommunityReportReason getReason() {
        return reason;
    }

    public void setReason(CommunityReportReason reason) {
        this.reason = reason;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail == null ? null : detail.trim();
    }
}
