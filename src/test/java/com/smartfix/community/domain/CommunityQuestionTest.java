package com.smartfix.community.domain;

import com.smartfix.common.exception.BusinessConflictException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules a question carries wherever it is used.
 *
 * <p>These are checked here rather than only through a form because the form is not the
 * only caller: the service builds a question from a command the controller has already
 * validated, and a controller test would pass even if the entity accepted a one-character
 * title. The limits are stated once, in this class, and the migration repeats them as
 * CHECK constraints so that a future path around the entity still cannot store one.</p>
 */
class CommunityQuestionTest {

    private static final Instant POSTED = Instant.parse("2026-09-20T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-09-20T09:00:00Z");
    private static final String BODY = "The network drops every few minutes near the reading room.";

    @Test
    void aPostedQuestionIsPublicOpenAndUnedited() {
        CommunityQuestion question = ask("Wifi drops in the library", BODY);

        assertThat(question.getStatus()).isEqualTo(CommunityContentStatus.VISIBLE);
        assertThat(question.isPubliclyVisible()).isTrue();
        assertThat(question.isSolved()).isFalse();
        assertThat(question.isEdited()).isFalse();
        assertThat(question.getUpdatedAt()).isEqualTo(question.getCreatedAt());
        // Nothing in this delivery can set it, and nothing a browser sends can reach it.
        assertThat(question.getAcceptedAnswerId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "a", ""})
    void aTitleShorterThanTheMinimumIsRefused(String title) {
        assertThatThrownBy(() -> ask(title, BODY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void aTitleOfExactlyTheMaximumIsAcceptedAndOneCharacterMoreIsNot() {
        String longest = "t".repeat(CommunityQuestion.TITLE_MAX_LENGTH);
        assertThat(ask(longest, BODY).getTitle()).hasSize(CommunityQuestion.TITLE_MAX_LENGTH);

        assertThatThrownBy(() -> ask(longest + "t", BODY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBodyShorterThanTheMinimumIsRefused() {
        assertThatThrownBy(() -> ask("Wifi drops in the library", "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body");
    }

    @Test
    void aBodyOfExactlyTheMaximumIsAcceptedAndOneCharacterMoreIsNot() {
        String longest = "b".repeat(CommunityQuestion.BODY_MAX_LENGTH);
        assertThat(ask("Wifi drops in the library", longest).getBody())
                .hasSize(CommunityQuestion.BODY_MAX_LENGTH);

        assertThatThrownBy(() -> ask("Wifi drops in the library", longest + "b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The limits apply to what is stored, not to what was typed, so a title padded out to
     * the minimum with spaces is still a two-character title.
     */
    @Test
    void surroundingWhitespaceIsTrimmedBeforeTheLimitsAreApplied() {
        assertThat(ask("  Wifi drops in the library  ", "\n " + BODY + " \n").getTitle())
                .isEqualTo("Wifi drops in the library");

        assertThatThrownBy(() -> ask("   ab   ", BODY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTitleOrBodyThatIsOnlyWhitespaceIsRefused() {
        assertThatThrownBy(() -> ask("     ", BODY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ask("Wifi drops in the library", "          "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEditChangesTheTitleTheBodyTheTopicAndTheTimestamp() {
        CommunityQuestion question = ask("Wifi drops in the library", BODY);

        question.edit("Wifi drops in the reading room", "It drops near the windows only.",
                CommunityCategory.NETWORK, LATER);

        assertThat(question.getTitle()).isEqualTo("Wifi drops in the reading room");
        assertThat(question.getCategory()).isEqualTo(CommunityCategory.NETWORK);
        assertThat(question.getUpdatedAt()).isEqualTo(LATER);
        assertThat(question.isEdited()).isTrue();
        // The author and the posting time are not reachable from an edit.
        assertThat(question.getAuthorId()).isEqualTo(7L);
        assertThat(question.getCreatedAt()).isEqualTo(POSTED);
    }

    @Test
    void anEditCannotTouchAQuestionThatIsNotPubliclyVisible() {
        CommunityQuestion withdrawn = ask("Wifi drops in the library", BODY);
        withdrawn.withdraw(LATER);

        assertThatThrownBy(() -> withdrawn.edit(
                "Something else entirely", BODY, CommunityCategory.OTHER, LATER))
                .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void withdrawingTakesTheQuestionOutOfPublicView() {
        CommunityQuestion question = ask("Wifi drops in the library", BODY);

        question.withdraw(LATER);

        assertThat(question.getStatus()).isEqualTo(CommunityContentStatus.WITHDRAWN);
        assertThat(question.isPubliclyVisible()).isFalse();
        assertThat(question.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void withdrawingTwiceIsANoOpRatherThanAnError() {
        CommunityQuestion question = ask("Wifi drops in the library", BODY);
        question.withdraw(POSTED.plusSeconds(60));

        question.withdraw(LATER);

        assertThat(question.getStatus()).isEqualTo(CommunityContentStatus.WITHDRAWN);
        // The second call must not have moved the timestamp either, or a double-submitted
        // form would rewrite when the withdrawal happened.
        assertThat(question.getUpdatedAt()).isEqualTo(POSTED.plusSeconds(60));
    }

    @Test
    void anAuthorCannotWithdrawAQuestionAModeratorHasHidden() {
        CommunityQuestion hidden = ask("Wifi drops in the library", BODY);
        setStatus(hidden, CommunityContentStatus.HIDDEN);

        assertThatThrownBy(() -> hidden.withdraw(LATER))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(hidden.getStatus()).isEqualTo(CommunityContentStatus.HIDDEN);
    }

    private CommunityQuestion ask(String title, String body) {
        return CommunityQuestion.ask(7L, title, body, CommunityCategory.NETWORK, POSTED);
    }

    /**
     * Hiding is a moderator action and no code in this delivery performs one, so the test
     * writes the field directly. Changing the field is the whole of what a moderation
     * action does to a question.
     */
    private static void setStatus(CommunityQuestion question, CommunityContentStatus status) {
        try {
            java.lang.reflect.Field field =
                    CommunityQuestion.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(question, status);
        } catch (ReflectiveOperationException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
