package com.smartfix.community.domain;

/**
 * Why a reader reported a piece of community content.
 *
 * <p>A closed set rather than free text, because the reason is what a moderator
 * sorts and counts by, and because a free-text reason would be a second, unvalidated
 * place for a reader to write anything at all. The detail a reporter wants to add
 * goes in the optional note beside this value, where it is length-bounded and shown
 * as text and never as markup.</p>
 *
 * <p>The constants are stored as their own names by
 * {@code V19}'s {@code chk_community_reports_reason}, which repeats this list, and
 * they are what the report form posts and what the queue prints. No display label is
 * invented here: the product shows the backend's own vocabulary for its enumerations
 * (categories and account statuses are shown the same way), and a second set of words
 * for the same five values would be a second thing to keep in step.</p>
 */
public enum CommunityReportReason {

    /** Advertising, or content posted repeatedly to no purpose. */
    SPAM,

    /** Harassment, threats, or content aimed at a person rather than a problem. */
    ABUSIVE,

    /** Genuine content, posted where it does not belong. */
    OFF_TOPIC,

    /** The same question or answer as another one already on the board. */
    DUPLICATE,

    /** Anything the reporter cannot place in the four above. */
    OTHER
}
