package com.smartfix.community.dto;

import com.smartfix.community.domain.CommunityReport;
import com.smartfix.community.domain.CommunityReportStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A moderator's decision on one report, as submitted by the queue's own form.
 *
 * <h2>Two decisions, and the third is not one of them</h2>
 *
 * <p>{@link CommunityReportStatus} has three constants and only two of them may be
 * submitted: upholding a report ({@code ACTIONED}) or dismissing it
 * ({@code DISMISSED}). {@code OPEN} is the absence of a decision, and a form that
 * could post it would be a way to put a settled report back in the queue. The field
 * is typed as the enum rather than as a boolean "uphold" flag so that the two outcomes
 * travel under the names the queue displays and the database stores; the service
 * refuses {@code OPEN} outright rather than mapping it onto one of the others.</p>
 *
 * <h2>Hiding is a separate question from upholding</h2>
 *
 * <p>{@code hideContent} is a checkbox of its own, because the two decisions are
 * genuinely independent: a report can be upheld with the content left up (it was
 * off-topic but not against the rules) and dismissed with the content hidden (the
 * report was wrong about the reason but right that something was wrong). Deriving one
 * from the other would take away a choice the moderator actually has.</p>
 *
 * <p>{@code handledByUserId} and {@code handledAt} are absent on purpose: the handler
 * is the authenticated account and the time is the server's clock.</p>
 */
public class ResolveReportCommand {

    @NotNull(message = "Choose whether the report is upheld or dismissed.")
    private CommunityReportStatus decision;

    @Size(max = CommunityReport.NOTE_MAX_LENGTH,
            message = "A note must be at most " + CommunityReport.NOTE_MAX_LENGTH
                    + " characters after trimming.")
    private String note;

    /**
     * Whether the decision also takes the reported content out of public view.
     *
     * <p>A primitive, so an unchecked checkbox arrives as {@code false} rather than as
     * {@code null} - the default has to be "leave the content alone".</p>
     */
    private boolean hideContent;

    public ResolveReportCommand() {
        // form binding
    }

    public CommunityReportStatus getDecision() {
        return decision;
    }

    public void setDecision(CommunityReportStatus decision) {
        this.decision = decision;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note == null ? null : note.trim();
    }

    public boolean isHideContent() {
        return hideContent;
    }

    public void setHideContent(boolean hideContent) {
        this.hideContent = hideContent;
    }
}
