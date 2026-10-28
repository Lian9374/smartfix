package com.smartfix.community.dto;

/**
 * The two lists {@code /community/mine} can show, per plan route A-R16.
 *
 * <p>{@code ?tab=QUESTIONS} and {@code ?tab=ANSWERS} are the same page because they are
 * the same question - "what have I posted here" - asked about the two things a member of
 * the board posts. An unrecognised value is a binding failure and answers 400, the same
 * way an unrecognised topic does on the board.</p>
 *
 * <p><strong>The spelling is the constant's name, in capitals.</strong> Spring binds an
 * enum by name, so {@code ?tab=answers} is <em>not</em> {@code ?tab=ANSWERS} and answers
 * 400 - the same is true of the board's {@code category} and {@code filter} parameters.
 * Every link is built from the constant and is therefore always right; the only place
 * that could get it wrong is a query string written out by hand, which is how this
 * parameter was first misspelled in a redirect (see {@code CommunityAnswerController
 * .withdraw}).</p>
 */
public enum MineTab {

    /** Everything this account has asked. The default, and what the route meant before. */
    QUESTIONS,

    /** Everything this account has answered. */
    ANSWERS
}
