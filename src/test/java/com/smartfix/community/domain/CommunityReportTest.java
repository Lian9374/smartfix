package com.smartfix.community.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules a report carries wherever it is built.
 *
 * <p>Checked here rather than only through the report form, for the reason
 * {@code CommunityQuestionTest} states: the form is not the only caller, and a test that
 * went through a controller would pass even if the entity accepted a report with no
 * target at all. The two factories are the only constructors, so "exactly one target" is
 * decided here - and {@code V19}'s {@code chk_community_reports_target} repeats it in
 * PostgreSQL, and the entity's own {@code @Check} repeats it in the H2 schema the tests
 * build, so a future path around these two methods still cannot store a row that breaks
 * it. That last part is verified where it can actually fail: see
 * {@code CommunityModerationPostgresIT}.</p>
 *
 * <p>What is <em>not</em> here is any way to write a decision. There is no setter and no
 * {@code resolve} method to test, because the four decision columns are written by one
 * conditional statement and by nothing else - the statement's own tests are in
 * {@code CommunityModerationServiceTest} and, for the race it exists to lose safely,
 * {@code CommunityModerationPostgresIT}.</p>
 */
class CommunityReportTest {

    private static final Long REPORTER = 5L;
    private static final Long QUESTION_ID = 42L;
    private static final Long ANSWER_ID = 100L;
    private static final Instant REPORTED = Instant.parse("2026-09-20T08:00:00Z");

    // ------------------------------------------------------------ one target

    @Test
    void aQuestionReportNamesTheQuestionAndNoAnswer() {
        CommunityReport report = CommunityReport.ofQuestion(
                REPORTER, QUESTION_ID, CommunityReportReason.SPAM, "Repeated three times.", REPORTED);

        assertThat(report.getQuestionId()).isEqualTo(QUESTION_ID);
        assertThat(report.getAnswerId()).isNull();
        assertThat(report.targetsQuestion()).isTrue();
        assertThat(report.getTargetId()).isEqualTo(QUESTION_ID);
        assertThat(report.getTargetType()).isEqualTo(CommunityContentType.QUESTION);
    }

    @Test
    void anAnswerReportNamesTheAnswerAndNoQuestion() {
        CommunityReport report = CommunityReport.ofAnswer(
                REPORTER, ANSWER_ID, CommunityReportReason.ABUSIVE, null, REPORTED);

        assertThat(report.getAnswerId()).isEqualTo(ANSWER_ID);
        assertThat(report.getQuestionId()).isNull();
        assertThat(report.targetsQuestion()).isFalse();
        assertThat(report.getTargetId()).isEqualTo(ANSWER_ID);
        assertThat(report.getTargetType()).isEqualTo(CommunityContentType.ANSWER);
    }

    /**
     * Both factories refuse a report with no target. The database refuses it too, and this
     * is the half of the rule that can be checked without a server: a {@code null} id is
     * the only way a Java caller could have expressed "no target", since neither factory
     * can express "both".
     */
    @Test
    void aReportWithoutItsTargetIsRefused() {
        assertThatThrownBy(() -> CommunityReport.ofQuestion(
                REPORTER, null, CommunityReportReason.SPAM, null, REPORTED))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> CommunityReport.ofAnswer(
                REPORTER, null, CommunityReportReason.SPAM, null, REPORTED))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------- a new report

    @Test
    void aNewReportIsOpenUnhandledAndStampeless() {
        CommunityReport report = CommunityReport.ofQuestion(
                REPORTER, QUESTION_ID, CommunityReportReason.OFF_TOPIC, null, REPORTED);

        assertThat(report.isOpen()).isTrue();
        assertThat(report.getStatus()).isEqualTo(CommunityReportStatus.OPEN);
        assertThat(report.getReporterId()).isEqualTo(REPORTER);
        assertThat(report.getReason()).isEqualTo(CommunityReportReason.OFF_TOPIC);
        assertThat(report.getCreatedAt()).isEqualTo(REPORTED);
        // Nothing about the handling exists yet, and nothing here can create it.
        assertThat(report.getHandledByUserId()).isNull();
        assertThat(report.getHandledAt()).isNull();
        assertThat(report.getResolutionNote()).isNull();
    }

    @Test
    void aReportWithoutAReporterOrAReasonIsRefused() {
        assertThatThrownBy(() -> CommunityReport.ofQuestion(
                null, QUESTION_ID, CommunityReportReason.SPAM, null, REPORTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reporterId");

        assertThatThrownBy(() -> CommunityReport.ofQuestion(
                REPORTER, QUESTION_ID, null, null, REPORTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");

        assertThatThrownBy(() -> CommunityReport.ofQuestion(
                REPORTER, QUESTION_ID, CommunityReportReason.SPAM, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }

    // ------------------------------------------------------------ the note

    @Test
    void theNoteIsTrimmedAndABlankOneIsStoredAsNoNoteAtAll() {
        assertThat(CommunityReport.ofQuestion(
                REPORTER, QUESTION_ID, CommunityReportReason.SPAM, "  see rule 4  ", REPORTED)
                .getDetail())
                .isEqualTo("see rule 4");

        // Empty and absent mean the same thing to a moderator, and storing "" for one of
        // them would be two spellings of "nothing" in the column V19 constrains.
        assertThat(CommunityReport.ofQuestion(
                REPORTER, QUESTION_ID, CommunityReportReason.SPAM, "   ", REPORTED)
                .getDetail())
                .isNull();
        assertThat(CommunityReport.ofQuestion(
                REPORTER, QUESTION_ID, CommunityReportReason.SPAM, null, REPORTED)
                .getDetail())
                .isNull();
    }

    @Test
    void aNoteAtTheLimitIsAcceptedAndOneCharacterMoreIsNot() {
        String longest = "n".repeat(CommunityReport.DETAIL_MAX_LENGTH);
        assertThat(CommunityReport.ofQuestion(
                REPORTER, QUESTION_ID, CommunityReportReason.OTHER, longest, REPORTED)
                .getDetail())
                .hasSize(CommunityReport.DETAIL_MAX_LENGTH);

        assertThatThrownBy(() -> CommunityReport.ofQuestion(
                REPORTER, QUESTION_ID, CommunityReportReason.OTHER, longest + "n", REPORTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("detail");
    }

    /**
     * The limit is measured after trimming, which is what makes this class, the DTO's
     * {@code @Size} and the migration's {@code char_length(...) BETWEEN 1 AND 500} agree:
     * all three read the value the trimming setter stored.
     */
    @Test
    void aNoteIsMeasuredAfterTrimmingNotBefore() {
        String atTheLimitWithPadding =
                "   " + "n".repeat(CommunityReport.DETAIL_MAX_LENGTH) + "   ";

        assertThat(CommunityReport.ofQuestion(
                REPORTER, QUESTION_ID, CommunityReportReason.OTHER, atTheLimitWithPadding, REPORTED)
                .getDetail())
                .hasSize(CommunityReport.DETAIL_MAX_LENGTH);
    }

    // ----------------------------------------------------------- the status

    @Test
    void onlyTheOpenStatusCountsAsWaitingForADecision() {
        assertThat(CommunityReportStatus.OPEN.isOpen()).isTrue();
        assertThat(CommunityReportStatus.ACTIONED.isOpen()).isFalse();
        assertThat(CommunityReportStatus.DISMISSED.isOpen()).isFalse();
    }
}
