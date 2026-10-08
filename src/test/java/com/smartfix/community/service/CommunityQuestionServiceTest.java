package com.smartfix.community.service;

import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.community.config.CommunityProperties;
import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityContentStatus;
import com.smartfix.community.domain.CommunityQuestion;
import com.smartfix.community.dto.QuestionFormCommand;
import com.smartfix.community.repository.CommunityQuestionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The write side: who a question is attributed to, who may change it, and what the posting
 * guards refuse.
 *
 * <p>Authorship and ownership are the two things a form cannot be trusted with, so they are
 * checked here rather than only through the pages. The controller IT confirms that a forged
 * field is discarded; this confirms that the service would not have used one if it arrived,
 * because no method takes an author other than as an explicit argument the controller reads
 * from the principal.</p>
 */
class CommunityQuestionServiceTest {

    private static final Long ACTOR = 7L;
    private static final Long SOMEBODY_ELSE = 8L;
    private static final Long QUESTION_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-09-20T08:00:00Z");

    private CommunityQuestionRepository questions;
    private CommunityAccessGuard access;
    private CommunityProperties properties;
    private CommunityQuestionService service;

    @BeforeEach
    void setUp() {
        questions = mock(CommunityQuestionRepository.class);
        access = mock(CommunityAccessGuard.class);
        properties = new CommunityProperties();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new CommunityQuestionService(questions, access, properties, clock);

        // Returns the same instance, as a real save does. Its id stays null: the id is the
        // database's to assign, so {@code ask}'s return value is not something a unit test
        // with a mock repository can assert on. The tests below assert that the save
        // happened and what was passed to it.
        when(questions.save(any(CommunityQuestion.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void askedQuestionIsAttributedToTheActingAccountAndStartsOpenAndPublic() {
        service.ask(command("Wifi drops in the library",
                "The network drops every few minutes near the reading room."), ACTOR);

        CommunityQuestion saved = capturedSave();
        assertThat(saved.getAuthorId()).isEqualTo(ACTOR);
        assertThat(saved.getStatus()).isEqualTo(CommunityContentStatus.VISIBLE);
        // A question can only be created open; there is no command field that could carry
        // an accepted answer, and the entity offers no way to set one.
        assertThat(saved.getAcceptedAnswerId()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void everyWriteChecksThatTheActingAccountIsStillActive() {
        when(access.requireActiveUser(ACTOR))
                .thenThrow(new ResourceNotFoundException("Community content not found."));

        assertThatThrownBy(() -> service.ask(command("Wifi drops in the library",
                "The network drops every few minutes near the reading room."), ACTOR))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.edit(QUESTION_ID, command("Wifi drops in the library",
                "The network drops every few minutes near the reading room."), ACTOR))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.withdraw(QUESTION_ID, ACTOR))
                .isInstanceOf(ResourceNotFoundException.class);

        // Not one of the three got as far as touching the repository.
        verify(questions, never()).save(any());
        verify(questions, never()).findByIdAndAuthorId(any(), any());
    }

    @Test
    void anIdenticalQuestionInsideTheWindowIsRefused() {
        when(questions
                .countByAuthorIdAndTitleIgnoreCaseAndBodyIgnoreCaseAndCreatedAtGreaterThanEqual(
                        eq(ACTOR), anyString(), anyString(), any()))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.ask(command("Wifi drops in the library",
                "The network drops every few minutes near the reading room."), ACTOR))
                .isInstanceOf(InputValidationException.class);
        verify(questions, never()).save(any());
    }

    @Test
    void theDuplicateCheckLooksBackExactlyTheConfiguredWindow() {
        service.ask(command("Wifi drops in the library",
                "The network drops every few minutes near the reading room."), ACTOR);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(questions)
                .countByAuthorIdAndTitleIgnoreCaseAndBodyIgnoreCaseAndCreatedAtGreaterThanEqual(
                        eq(ACTOR), anyString(), anyString(), since.capture());

        assertThat(since.getValue())
                .isEqualTo(NOW.minus(properties.getPosting().getDuplicateWindow()));
    }

    @Test
    void aDifferentQuestionByTheSameAuthorIsNotADuplicate() {
        when(questions
                .countByAuthorIdAndTitleIgnoreCaseAndBodyIgnoreCaseAndCreatedAtGreaterThanEqual(
                        eq(ACTOR), anyString(), anyString(), any()))
                .thenReturn(0L);

        service.ask(command("Projector will not power on",
                "The projector does not respond at all."), ACTOR);

        verify(questions).save(any(CommunityQuestion.class));
    }

    @Test
    void postingIsRefusedOnceTheRateLimitIsReached() {
        properties.getPosting().setMaxPerRateLimitWindow(3);
        when(questions.countByAuthorIdAndCreatedAtGreaterThanEqual(eq(ACTOR), any()))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.ask(command("Wifi drops in the library",
                "The network drops every few minutes near the reading room."), ACTOR))
                .isInstanceOf(InputValidationException.class);
        verify(questions, never()).save(any());
    }

    @Test
    void oneBelowTheRateLimitStillPosts() {
        properties.getPosting().setMaxPerRateLimitWindow(3);
        when(questions.countByAuthorIdAndCreatedAtGreaterThanEqual(eq(ACTOR), any()))
                .thenReturn(2L);

        service.ask(command("Wifi drops in the library",
                "The network drops every few minutes near the reading room."), ACTOR);

        verify(questions).save(any(CommunityQuestion.class));
    }

    /**
     * The plan offers turning the guards off as an option, so the switch has to actually
     * switch them off - and it has to be off for both of them, not just the duplicate check.
     */
    @Test
    void disablingDuplicateDetectionTurnsOffBothGuards() {
        properties.getPosting().setDuplicateDetectionEnabled(false);
        when(questions
                .countByAuthorIdAndTitleIgnoreCaseAndBodyIgnoreCaseAndCreatedAtGreaterThanEqual(
                        eq(ACTOR), anyString(), anyString(), any()))
                .thenReturn(9L);
        when(questions.countByAuthorIdAndCreatedAtGreaterThanEqual(eq(ACTOR), any()))
                .thenReturn(999L);

        service.ask(command("Wifi drops in the library",
                "The network drops every few minutes near the reading room."), ACTOR);

        verify(questions).save(any(CommunityQuestion.class));
        verify(questions, never()).countByAuthorIdAndCreatedAtGreaterThanEqual(any(), any());
    }

    @Test
    void aZeroDuplicateWindowSkipsTheDuplicateCheckButKeepsTheRateLimit() {
        properties.getPosting().setDuplicateWindow(Duration.ZERO);
        when(questions.countByAuthorIdAndCreatedAtGreaterThanEqual(eq(ACTOR), any()))
                .thenReturn(999L);

        assertThatThrownBy(() -> service.ask(command("Wifi drops in the library",
                "The network drops every few minutes near the reading room."), ACTOR))
                .isInstanceOf(InputValidationException.class);
        verify(questions, never())
                .countByAuthorIdAndTitleIgnoreCaseAndBodyIgnoreCaseAndCreatedAtGreaterThanEqual(
                        any(), any(), any(), any());
    }

    @Test
    void editingSomeoneElsesQuestionIsNotFound() {
        // The query is what filters by author, so it returns empty for a stranger rather
        // than returning a row the service then has to remember to check.
        when(questions.findByIdAndAuthorId(QUESTION_ID, SOMEBODY_ELSE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(QUESTION_ID,
                command("Wifi drops in the library",
                        "The network drops every few minutes near the reading room."),
                SOMEBODY_ELSE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void withdrawingSomeoneElsesQuestionIsNotFound() {
        when(questions.findByIdAndAuthorId(QUESTION_ID, SOMEBODY_ELSE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(QUESTION_ID, SOMEBODY_ELSE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void editingOwnQuestionAppliesTheChange() {
        CommunityQuestion mine = question();
        when(questions.findByIdAndAuthorId(QUESTION_ID, ACTOR)).thenReturn(Optional.of(mine));

        service.edit(QUESTION_ID, command("Wifi drops in the reading room",
                "It drops near the windows only."), ACTOR);

        assertThat(mine.getTitle()).isEqualTo("Wifi drops in the reading room");
        assertThat(mine.getBody()).isEqualTo("It drops near the windows only.");
        assertThat(mine.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void withdrawingOwnQuestionTakesItOutOfPublicView() {
        CommunityQuestion mine = question();
        when(questions.findByIdAndAuthorId(QUESTION_ID, ACTOR)).thenReturn(Optional.of(mine));

        service.withdraw(QUESTION_ID, ACTOR);

        assertThat(mine.getStatus()).isEqualTo(CommunityContentStatus.WITHDRAWN);
    }

    @Test
    void aNullQuestionIdIsNotFound() {
        assertThatThrownBy(() -> service.withdraw(null, ACTOR))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(questions);
    }

    private QuestionFormCommand command(String title, String body) {
        QuestionFormCommand command = new QuestionFormCommand();
        command.setTitle(title);
        command.setBody(body);
        command.setCategory(CommunityCategory.NETWORK);
        return command;
    }

    private CommunityQuestion question() {
        return CommunityQuestion.ask(ACTOR, "Wifi drops in the library",
                "The network drops every few minutes near the reading room.",
                CommunityCategory.NETWORK, NOW.minusSeconds(600));
    }

    private CommunityQuestion capturedSave() {
        ArgumentCaptor<CommunityQuestion> saved = ArgumentCaptor.forClass(CommunityQuestion.class);
        verify(questions).save(saved.capture());
        return saved.getValue();
    }
}
