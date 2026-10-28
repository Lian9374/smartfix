package com.smartfix.community.domain;

/**
 * Where a report stands in the moderation queue.
 *
 * <p>Three states, and the two settled ones are kept apart because they mean
 * different things about the content rather than different amounts of effort:</p>
 *
 * <ul>
 *   <li>{@link #OPEN} - waiting for a moderator. The queue's only listing, and the
 *       only state in which a decision may be recorded.</li>
 *   <li>{@link #ACTIONED} - a moderator agreed with the report. The decision may have
 *       hidden the content or may have left it public; the status records that the
 *       report was upheld, not what was done about the content, which the content's
 *       own status records.</li>
 *   <li>{@link #DISMISSED} - a moderator disagreed. The content is untouched by the
 *       decision unless the moderator hid it at the same time, which the form allows
 *       and which the content's own status shows.</li>
 * </ul>
 *
 * <p>Only {@code OPEN} may be left. A settled report is never returned to the queue
 * and its decision is never rewritten: a second moderator acting on a report somebody
 * else has already settled changes nothing, so the record of who decided what, and
 * when, stays a record of one decision. That is what the conditional
 * {@code resolveIfOpen} statement in {@code CommunityReportRepository} enforces, and
 * what {@code V19}'s {@code chk_community_reports_handled} keeps consistent with
 * {@code handled_at}.</p>
 */
public enum CommunityReportStatus {

    OPEN,
    ACTIONED,
    DISMISSED;

    /** Whether a moderator may still record a decision for this state. */
    public boolean isOpen() {
        return this == OPEN;
    }
}
