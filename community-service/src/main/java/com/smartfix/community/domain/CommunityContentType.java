package com.smartfix.community.domain;

/**
 * Which kind of community content a moderation action or an event refers to.
 *
 * <p>Needed because hiding and withdrawing apply to questions and to answers alike, and
 * the consuming modules (notification, audit) have to know which one a bare id names.
 * Without it, {@code CommunityContentHiddenEvent} would carry an id whose meaning depends
 * on which publisher sent it - exactly the kind of implicit context the event payload
 * rules in plan section 12.6 exist to prevent.</p>
 */
public enum CommunityContentType {

    QUESTION,
    ANSWER
}
