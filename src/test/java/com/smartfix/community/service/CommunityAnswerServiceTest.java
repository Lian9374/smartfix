package com.smartfix.community.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.community.config.CommunityProperties;
import com.smartfix.community.domain.CommunityAnswer;
import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityContentStatus;
import com.smartfix.community.domain.CommunityContentType;
import com.smartfix.community.domain.CommunityQuestion;
import com.smartfix.community.dto.AnswerFormCommand;
import com.smartfix.community.event.CommunityAnswerAcceptedEvent;
import com.smartfix.community.event.CommunityAnswerCreatedEvent;
import com.smartfix.community.event.CommunityContentHiddenEvent;
import com.smartfix.community.repository.CommunityAnswerRepository;
import com.smartfix.community.repository.CommunityQuestionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The write side for answers: who may do what, which refusals are 404s and which are
 * conflicts, and what the lock and the conditional update each guarantee.
 *
 * <h2>What a mock repository can and cannot prove</h2>
 *
 * <p>This file proves that the service asks the right questions in the right order and
 * raises the right refusal. It cannot prove that two transactions racing actually behave,
 * because a mock has no transactions and no second connection: the affected-row count is
 * simply whatever the stub was told to return. That is what
 * {@code CommunityAnswerConcurrencyPostgresIT} is for, and these tests deliberately do not
 * stand in for it.</p>
 */
class CommunityAnswerServiceTest {

    private static final Long ASKER = 7L;
    private static final Long ANSWERER = 8L;
    private static final Long STRANGER = 9L;
    private static final Long QUESTION_ID = 42L;
    private static final Long ANSWER_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-09-20T08:00:00Z");

    private CommunityQuestionRepository questions;
    private CommunityAnswerRepository answers;
    private CommunityAccessGuard access;
    private CommunityProperties properties;
    private ApplicationEventPublisher events;
    private CommunityAnswerService service;

    @BeforeEach
    void setUp() {
        questions = mock(CommunityQuestionRepository.class);
        answers = mock(CommunityAnswerRepository.class);
        access = mock(CommunityAccessGuard.class);
        properties = new CommunityProperties();
        events = mock(ApplicationEventPublisher.class);
        service = new CommunityAnswerService(questions, answers, access, properties, events,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(answers.save(any(CommunityAnswer.class))).thenAnswer(call -> call.getArgument(0));
    }

    // -------------------------------------------------------------- posting

    @Test
    void aPostedAnswerIsAttributedToTheActingAccountAndCarriesItsQuestion() {
        when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));

        service.post(QUESTION_ID, command("Have you tried the power cable?"), ANSWERER);

        CommunityAnswer saved = capturedSave();
        assertThat(saved.getAuthorId()).isEqualTo(ANSWERER);
        assertThat(saved.getQuestionId()).isEqualTo(QUESTION_ID);
        assertThat(saved.getStatus()).isEqualTo(CommunityContentStatus.VISIBLE);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void postingAnnouncesTheAnswerWithBothAuthorsInIt() {
        when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));

        service.post(QUESTION_ID, command("Have you tried the power cable?"), ANSWERER);

        CommunityAnswerCreatedEvent event = capturedEvent(CommunityAnswerCreatedEvent.class);
        assertThat(event.questionId()).isEqualTo(QUESTION_ID);
        assertThat(event.questionAuthorId()).isEqualTo(ASKER);
        assertThat(event.answerAuthorId()).isEqualTo(ANSWERER);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void answeringAQuestionThatIsNotPublicIsNotFound() {
        CommunityQuestion withdrawn = question(ASKER);
        withdrawn.withdraw(NOW);
        when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.post(QUESTION_ID,
                command("Have you tried the power cable?"), ANSWERER))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(answers, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void answeringIsRefusedOnceTheRateLimitIsReached() {
        properties.getPosting().setMaxPerRateLimitWindow(3);
        when(answers.countByAuthorIdAndCreatedAtGreaterThanEqual(eq(ANSWERER), any()))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.post(QUESTION_ID,
                command("Have you tried the power cable?"), ANSWERER))
                .isInstanceOf(InputValidationException.class);
        // The guard runs before the question is read, so a flood cannot even probe for
        // question ids.
        verifyNoInteractions(questions);
        verify(answers, never()).save(any());
    }

    /** The switch turns off the answer guard too, which is what "turn the guards off" means. */
    @Test
    void disablingTheGuardsTurnsOffTheAnswerRateLimitAsWell() {
        properties.getPosting().setDuplicateDetectionEnabled(false);
        when(answers.countByAuthorIdAndCreatedAtGreaterThanEqual(eq(ANSWERER), any()))
                .thenReturn(999L);
        when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));

        service.post(QUESTION_ID, command("Have you tried the power cable?"), ANSWERER);

        verify(answers, never()).countByAuthorIdAndCreatedAtGreaterThanEqual(any(), any());
        verify(answers).save(any(CommunityAnswer.class));
    }

    @Test
    void anInactiveAccountCanDoNothing() {
        when(access.requireActiveUser(STRANGER))
                .thenThrow(new ResourceNotFoundException("Community content not found."));

        assertThatThrownBy(() -> service.post(QUESTION_ID,
                command("Have you tried the power cable?"), STRANGER))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.edit(ANSWER_ID,
                command("Have you tried the power cable?"), STRANGER))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.withdraw(ANSWER_ID, STRANGER))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.accept(QUESTION_ID, ANSWER_ID, STRANGER))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.removeAcceptance(QUESTION_ID, STRANGER))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(questions, answers);
        verifyNoInteractions(events);
    }

    // -------------------------------------------------------------- editing

    @Test
    void editingSomeoneElsesAnswerIsNotFound() {
        // The repository query filters by author, so a stranger's attempt returns nothing
        // rather than a row the service has to remember to check.
        when(answers.findByIdAndAuthorId(ANSWER_ID, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(ANSWER_ID,
                command("A replacement body for somebody else's answer."), STRANGER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void editingOwnAnswerRewritesTheBodyAndTheTimestamp() {
        CommunityAnswer mine = answer(ANSWERER);
        when(answers.findByIdAndAuthorId(ANSWER_ID, ANSWERER)).thenReturn(Optional.of(mine));

        service.edit(ANSWER_ID, command("The corrected body of my answer."), ANSWERER);

        assertThat(mine.getBody()).isEqualTo("The corrected body of my answer.");
        assertThat(mine.getUpdatedAt()).isEqualTo(NOW);
        assertThat(mine.getAuthorId()).isEqualTo(ANSWERER);
        assertThat(mine.getQuestionId()).isEqualTo(QUESTION_ID);
    }

    @Test
    void editingAnAnswerThatIsNoLongerVisibleIsAConflict() {
        CommunityAnswer withdrawn = answer(ANSWERER);
        withdrawn.withdraw(NOW);
        when(answers.findByIdAndAuthorId(ANSWER_ID, ANSWERER)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.edit(ANSWER_ID,
                command("The corrected body of my answer."), ANSWERER))
                .isInstanceOf(BusinessConflictException.class);
    }

    // ----------------------------------------------------------- withdrawing

    @Test
    void withdrawingTakesTheAnswerOutOfViewAndTellsTheQuestion() {
        when(answers.findQuestionIdOfOwnAnswer(ANSWER_ID, ANSWERER))
                .thenReturn(Optional.of(QUESTION_ID));
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(answer(ANSWERER)));

        service.withdraw(ANSWER_ID, ANSWERER);

        assertThat(answers.findById(ANSWER_ID).orElseThrow().getStatus())
                .isEqualTo(CommunityContentStatus.WITHDRAWN);
        // Nothing was accepted on this question, so nothing was cleared.
        verify(questions, never()).clearAcceptanceIfPresent(any(), any());

        CommunityContentHiddenEvent event = capturedEvent(CommunityContentHiddenEvent.class);
        assertThat(event.contentId()).isEqualTo(ANSWER_ID);
        assertThat(event.contentType()).isEqualTo(CommunityContentType.ANSWER);
        assertThat(event.questionId()).isEqualTo(QUESTION_ID);
        assertThat(event.resultingStatus()).isEqualTo(CommunityContentStatus.WITHDRAWN);
        assertThat(event.actorUserId()).isEqualTo(ANSWERER);
    }

    /**
     * The two rows move together. A question left pointing at a withdrawn answer would read
     * "Solved" with nothing under it, which is the state the derived-solved rule exists to
     * prevent - so the clearing happens in the same call, and only when the question really
     * does point at this answer.
     */
    @Test
    void withdrawingTheAcceptedAnswerOpensTheQuestionInTheSameCall() {
        CommunityQuestion accepted = acceptedQuestion(ASKER, ANSWER_ID);
        when(answers.findQuestionIdOfOwnAnswer(ANSWER_ID, ANSWERER))
                .thenReturn(Optional.of(QUESTION_ID));
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(accepted));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(answer(ANSWERER)));

        service.withdraw(ANSWER_ID, ANSWERER);

        verify(questions).clearAcceptanceIfPresent(QUESTION_ID, NOW);
    }

    @Test
    void withdrawingSomebodyElsesAnswerIsNotFoundAndTakesNoLock() {
        when(answers.findQuestionIdOfOwnAnswer(ANSWER_ID, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(ANSWER_ID, STRANGER))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(questions, never()).findByIdForUpdate(any());
        verifyNoInteractions(events);
    }

    @Test
    void withdrawingTwiceChangesNothingAndRaisesNoSecondEvent() {
        CommunityAnswer alreadyWithdrawn = answer(ANSWERER);
        alreadyWithdrawn.withdraw(NOW.minusSeconds(30));
        when(answers.findQuestionIdOfOwnAnswer(ANSWER_ID, ANSWERER))
                .thenReturn(Optional.of(QUESTION_ID));
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(alreadyWithdrawn));

        service.withdraw(ANSWER_ID, ANSWERER);

        assertThat(alreadyWithdrawn.getUpdatedAt()).isEqualTo(NOW.minusSeconds(30));
        verifyNoInteractions(events);
    }

    /**
     * The question is locked before the answer is read, and the answer is reached through a
     * scalar query first so that it is not already in the persistence context when the lock
     * is taken. Reading it earlier would let a stale copy decide the outcome.
     */
    @Test
    void withdrawingLocksTheQuestionBeforeItLooksAtTheAnswer() {
        when(answers.findQuestionIdOfOwnAnswer(ANSWER_ID, ANSWERER))
                .thenReturn(Optional.of(QUESTION_ID));
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(answer(ANSWERER)));

        service.withdraw(ANSWER_ID, ANSWERER);

        InOrder order = inOrder(questions, answers);
        order.verify(answers).findQuestionIdOfOwnAnswer(ANSWER_ID, ANSWERER);
        order.verify(questions).findByIdForUpdate(QUESTION_ID);
        order.verify(answers).findById(ANSWER_ID);
    }

    // ------------------------------------------------------------ accepting

    @Test
    void theQuestionsAuthorAcceptsAVisibleAnswer() {
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(answer(ANSWERER)));
        when(questions.acceptAnswerIfOpen(QUESTION_ID, ANSWER_ID, NOW)).thenReturn(1);

        service.accept(QUESTION_ID, ANSWER_ID, ASKER);

        CommunityAnswerAcceptedEvent event = capturedEvent(CommunityAnswerAcceptedEvent.class);
        assertThat(event.answerId()).isEqualTo(ANSWER_ID);
        assertThat(event.questionId()).isEqualTo(QUESTION_ID);
        assertThat(event.questionAuthorId()).isEqualTo(ASKER);
        assertThat(event.answerAuthorId()).isEqualTo(ANSWERER);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    /**
     * The conditional update is the invariant: it affects no row when the question already
     * has an accepted answer, and that is turned into a conflict rather than into a silent
     * second acceptance. No event is published for an acceptance that did not happen.
     */
    @Test
    void anAcceptanceThatTheConditionalUpdateRefusedIsAConflictAndAnnouncesNothing() {
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(answer(ANSWERER)));
        when(questions.acceptAnswerIfOpen(QUESTION_ID, ANSWER_ID, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.accept(QUESTION_ID, ANSWER_ID, ASKER))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("already has an accepted answer");
        verifyNoInteractions(events);
    }

    @Test
    void acceptingOnSomebodyElsesQuestionIsNotFoundAndNeverReachesTheUpdate() {
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));

        assertThatThrownBy(() -> service.accept(QUESTION_ID, ANSWER_ID, STRANGER))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(questions, never()).acceptAnswerIfOpen(any(), any(), any());
        verify(answers, never()).findById(any());
    }

    /**
     * An answer id taken from another question. It is a 404 and not a conflict: a conflict
     * would confirm that the id exists somewhere, which is what the 404 is there to hide.
     */
    @Test
    void acceptingAnAnswerThatBelongsToAnotherQuestionIsNotFound() {
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));
        CommunityAnswer foreign = CommunityAnswer.post(QUESTION_ID + 1, ANSWERER,
                "An answer to a different question entirely.", NOW.minusSeconds(60));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.accept(QUESTION_ID, ANSWER_ID, ASKER))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(questions, never()).acceptAnswerIfOpen(any(), any(), any());
    }

    /** Rule 3 precedes rule 4, so a hidden foreign answer is still a 404 and not a conflict. */
    @Test
    void aHiddenAnswerFromAnotherQuestionIsStillNotFoundRatherThanAConflict() {
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));
        CommunityAnswer foreign = CommunityAnswer.post(QUESTION_ID + 1, ANSWERER,
                "An answer to a different question entirely.", NOW.minusSeconds(60));
        foreign.withdraw(NOW);
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.accept(QUESTION_ID, ANSWER_ID, ASKER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void acceptingAnAnswerThatIsNoLongerVisibleIsAConflict() {
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));
        CommunityAnswer withdrawn = answer(ANSWERER);
        withdrawn.withdraw(NOW.minusSeconds(30));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.accept(QUESTION_ID, ANSWER_ID, ASKER))
                .isInstanceOf(BusinessConflictException.class);
        verify(questions, never()).acceptAnswerIfOpen(any(), any(), any());
    }

    @Test
    void acceptingOnAQuestionThatIsNoLongerVisibleIsAConflict() {
        CommunityQuestion withdrawn = question(ASKER);
        withdrawn.withdraw(NOW.minusSeconds(30));
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.accept(QUESTION_ID, ANSWER_ID, ASKER))
                .isInstanceOf(BusinessConflictException.class);
        verify(answers, never()).findById(any());
    }

    /**
     * ADR-003 fixes D-05 as forbidden self-acceptance, enforced by the real write service.
     */
    @Test
    void theQuestionsAuthorCannotAcceptTheirOwnAnswer() {
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(answer(ASKER)));

        assertThatThrownBy(() -> service.accept(QUESTION_ID, ANSWER_ID, ASKER))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("cannot accept your own answer");
        verify(questions, never()).acceptAnswerIfOpen(any(), any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void acceptingLocksTheQuestionBeforeItLooksAtTheAnswer() {
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(answer(ANSWERER)));
        when(questions.acceptAnswerIfOpen(QUESTION_ID, ANSWER_ID, NOW)).thenReturn(1);

        service.accept(QUESTION_ID, ANSWER_ID, ASKER);

        InOrder order = inOrder(questions, answers);
        order.verify(questions).findByIdForUpdate(QUESTION_ID);
        order.verify(answers).findById(ANSWER_ID);
        order.verify(questions).acceptAnswerIfOpen(QUESTION_ID, ANSWER_ID, NOW);
    }

    @Test
    void aNullIdOnAnAcceptRouteIsNotFoundRatherThanAnError() {
        assertThatThrownBy(() -> service.accept(null, ANSWER_ID, ASKER))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.accept(QUESTION_ID, null, ASKER))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(questions, answers);
    }

    // -------------------------------------------------------- un-accepting

    /**
     * The service delegates and nothing else. Idempotence is the statement's own - the
     * clearing affects no row when there is nothing to clear - so there is no branch here
     * to get wrong, and no event for a question that merely went from solved to open.
     */
    @Test
    void removingTheAcceptanceClearsItAndAnnouncesNothing() {
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));

        service.removeAcceptance(QUESTION_ID, ASKER);

        verify(questions).clearAcceptanceIfPresent(QUESTION_ID, NOW);
        verify(answers, never()).findById(any());
        verifyNoInteractions(events);
    }

    @Test
    void removingTheAcceptanceOnSomebodyElsesQuestionIsNotFound() {
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question(ASKER)));

        assertThatThrownBy(() -> service.removeAcceptance(QUESTION_ID, STRANGER))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(questions, never()).clearAcceptanceIfPresent(any(), any());
    }

    // -------------------------------------------------------------- fixtures

    private AnswerFormCommand command(String body) {
        AnswerFormCommand command = new AnswerFormCommand();
        command.setBody(body);
        return command;
    }

    /**
     * A stored question, so it has an id.
     *
     * <p>The id is assigned by the database in production and a mock repository never
     * inserts anything, so the fixture writes it on. Without it a posted answer would carry
     * a null question id - which is exactly what the entity's own null check catches.</p>
     */
    private CommunityQuestion question(Long authorId) {
        CommunityQuestion question = CommunityQuestion.ask(authorId, "Projector will not power on",
                "The projector in seminar room three shows no picture at all.",
                CommunityCategory.HARDWARE, NOW.minusSeconds(600));
        ReflectionTestUtils.setField(question, "id", QUESTION_ID);
        return question;
    }

    /**
     * A question in the solved state.
     *
     * <p>The accepted answer is written straight onto the field, and this is the only way a
     * test can reach that state without a database: the design deliberately gives the entity
     * no setter, so that no entity write path can change an accepted answer. In production
     * the state is reached by {@code CommunityQuestionRepository.acceptAnswerIfOpen}, which
     * a mocked repository does not run - so the fixture stands in for it here and nowhere
     * else.</p>
     */
    private CommunityQuestion acceptedQuestion(Long authorId, Long acceptedAnswerId) {
        CommunityQuestion question = question(authorId);
        ReflectionTestUtils.setField(question, "acceptedAnswerId", acceptedAnswerId);
        return question;
    }

    private CommunityAnswer answer(Long authorId) {
        return CommunityAnswer.post(QUESTION_ID, authorId,
                "Have you tried the power cable?", NOW.minusSeconds(60));
    }

    private CommunityAnswer capturedSave() {
        ArgumentCaptor<CommunityAnswer> saved = ArgumentCaptor.forClass(CommunityAnswer.class);
        verify(answers).save(saved.capture());
        return saved.getValue();
    }

    private <T> T capturedEvent(Class<T> type) {
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        return type.cast(published.getValue());
    }
}
