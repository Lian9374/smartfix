package com.smartfix.community.domain;

/**
 * Publication state shared by community questions and community answers.
 *
 * <p>Three states, and the distinction between the two non-public ones is the
 * point:</p>
 *
 * <ul>
 *   <li>{@link #VISIBLE} - published. The only state a public list, a search or a
 *       public count may return.</li>
 *   <li>{@link #HIDDEN} - taken out of public view by moderation, not by its
 *       author. Content is retained so the decision can be reviewed and reversed,
 *       which is why hiding is a status and never a delete.</li>
 *   <li>{@link #WITHDRAWN} - taken out of public view by its own author. Retained
 *       for the same reason.</li>
 * </ul>
 *
 * <p>Neither non-public state erases the row. Plan section 12 and the sprint 3
 * brief both forbid physically cascade-deleting user content, so "removed" is
 * always a state change and an author's own withdrawn question stays reachable
 * to that author in "my questions".</p>
 */
public enum CommunityContentStatus {

    VISIBLE,
    HIDDEN,
    WITHDRAWN;

    /** Whether this state may appear in a public list, search result or count. */
    public boolean isPubliclyVisible() {
        return this == VISIBLE;
    }
}
