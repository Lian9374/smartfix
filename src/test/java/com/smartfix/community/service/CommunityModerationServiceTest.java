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
import com.smartfix.community.domain.CommunityReport;
import com.smartfix.community.domain.CommunityReportReason;
import com.smartfix.community.domain.CommunityReportStatus;
import com.smartfix.community.dto.CommunityReportResponse;
import com.smartfix.community.dto.ReportContentCommand;
import com.smartfix.community.dto.ResolveReportCommand;
import com.smartfix.community.event.CommunityContentHiddenEvent;
import com.smartfix.community.repository.CommunityAnswerRepository;
import com.smartfix.community.repository.CommunityQuestionRepository;
import com.smartfix.community.repository.CommunityReportRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Reporting, the queue, the decisions and the two governance actions - with mocked
 * repositories.
 *
 * <h2>What a mock can prove, and what it cannot</h2>
 *
 * <p>This file proves that each method asks the right questions in the right order, raises
 * the right refusal, and - for the two things that are about order rather than about values
 * - that the order is the one the class comment claims. The two statements that carry the
 * module's hardest guarantees are the conditional decision update and the question-row
 * lock, and neither can be settled here: a mock has no rows to affect and no second
 * connection to contend with, so the affected-row count is whatever the stub was told to
 * return. What is checked here is that the service <em>reads</em> that count and lets it
 * decide everything, including whether the content is hidden at all. The database
 * behaviour itself - two moderators deciding at once, an acceptance racing a hide, and the
 * duplicate-report index - is in {@code CommunityModerationPostgresIT}, and the tests that
 * depend on it say so.</p>
 */
class CommunityModerationServiceTest {

    private static final Long ADMIN = 1L;
    private static final Long REPORTER = 5L;
    private static final Long AUTHOR = 7L;
    private static final Long QUESTION_ID = 42L;
    private static final Long ANSWER_ID = 100L;
    private static final Long REPORT_ID = 900L;
    private static final Instant NOW = Instant.parse("2026-09-20T08:00:00Z");

    private CommunityQuestionRepository questions;
    private CommunityAnswerRepository answers;
    private CommunityReportRepository reports;
    private CommunityAccessGuard access;
    private ApplicationEventPublisher events;
    private CommunityModerationService service;

    @BeforeEach
    void setUp() {
        questions = mock(CommunityQuestionRepository.class);
        answers = mock(CommunityAnswerRepository.class);
        reports = mock(CommunityReportRepository.class);
        access = mock(CommunityAccessGuard.class);
        events = mock(ApplicationEventPublisher.class);
        service = new CommunityModerationService(questions, answers, reports, access,
                new CommunityProperties(), events, Clock.fixed(NOW, ZoneOffset.UTC),
                mock(com.smartfix.audit.service.AuditService.class));

        when(reports.saveAndFlush(any(CommunityReport.class)))
                .thenAnswer(call -> withId(call.getArgument(0)));
    }

    // ------------------------------------------------------------- reporting

    @Test
    void reportingAQuestionStoresItAgainstTheActingAccountAndTheRoute() {
        when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(question()));
        when(reports.existsByReporterIdAndQuestionId(REPORTER, QUESTION_ID)).thenReturn(false);

        assertThat(service.reportQuestion(QUESTION_ID,
                report(CommunityReportReason.SPAM, "  noise  "), REPORTER))
                .isEqualTo(REPORT_ID);

        CommunityReport saved = capturedReport();
        assertThat(saved.getReporterId()).isEqualTo(REPORTER);
        assertThat(saved.getQuestionId()).isEqualTo(QUESTION_ID);
        assertThat(saved.getAnswerId()).isNull();
        assertThat(saved.getReason()).isEqualTo(CommunityReportReason.SPAM);
        assertThat(saved.getDetail()).isEqualTo("noise");
        assertThat(saved.getStatus()).isEqualTo(CommunityReportStatus.OPEN);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    /**
     * The duplicate is refused before the insert, so the ordinary double-click gets a
     * sentence rather than a database error, and nothing is written.
     */
    @Test
    void reportingTheSameQuestionTwiceIsRefusedBeforeAnythingIsWritten() {
        when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(question()));
        when(reports.existsByReporterIdAndQuestionId(REPORTER, QUESTION_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.reportQuestion(QUESTION_ID,
                report(CommunityReportReason.SPAM, null), REPORTER))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("already reported");

        verify(reports, never()).saveAndFlush(any());
    }

    /**
     * The race the pre-check cannot win: two requests both find no report and both insert.
     * One of them meets {@code V19}'s unique index, and it has to come back as the same
     * refusal the check would have given rather than as a 500.
     *
     * <p>The check is stubbed to answer "no" on purpose - that is exactly what the losing
     * request sees before it inserts, and it is why the catch is not dead code.</p>
     */
    @Test
    void aDuplicateThatSlipsPastTheCheckIsRefusedTheSameWayRatherThanFailing() {
        when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(question()));
        when(reports.existsByReporterIdAndQuestionId(REPORTER, QUESTION_ID)).thenReturn(false);
        when(reports.saveAndFlush(any(CommunityReport.class)))
                .thenThrow(new DataIntegrityViolationException("uk_community_reports_question"));

        assertThatThrownBy(() -> service.reportQuestion(QUESTION_ID,
                report(CommunityReportReason.SPAM, null), REPORTER))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("already reported");
    }

    @Test
    void aQuestionThatIsNotPublicCannotBeReported() {
        CommunityQuestion withdrawn = question();
        withdrawn.withdraw(NOW);
        when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.reportQuestion(QUESTION_ID,
                report(CommunityReportReason.SPAM, null), REPORTER))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(reports, never()).saveAndFlush(any());
    }

    @Test
    void reportingAnAnswerNeedsBothTheAnswerAndItsQuestionToBePublic() {
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(answer()));
        when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(question()));
        when(reports.existsByReporterIdAndAnswerId(REPORTER, ANSWER_ID)).thenReturn(false);

        assertThat(service.reportAnswer(ANSWER_ID,
                report(CommunityReportReason.ABUSIVE, null), REPORTER))
                .isEqualTo(REPORT_ID);

        CommunityReport saved = capturedReport();
        assertThat(saved.getAnswerId()).isEqualTo(ANSWER_ID);
        // The answer's question is deliberately not recorded on the report.
        assertThat(saved.getQuestionId()).isNull();

        // The same answer under a withdrawn question: reachable from no public page, so
        // there is nothing for a moderator to act on.
        CommunityQuestion withdrawn = question();
        withdrawn.withdraw(NOW);
        when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.reportAnswer(ANSWER_ID,
                report(CommunityReportReason.ABUSIVE, null), REPORTER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anAnswerThatIsNotPublicCannotBeReportedAndItsQuestionIsNotEvenRead() {
        CommunityAnswer withdrawn = answer();
        withdrawn.withdraw(NOW);
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.reportAnswer(ANSWER_ID,
                report(CommunityReportReason.ABUSIVE, null), REPORTER))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(questions);
    }

    /**
     * An unusable account is refused before anything is read. The request reached the
     * controller, so {@code ActiveAccountFilter} let it through; the guard is what makes the
     * rule hold for a caller that did not come through a route.
     */
    @Test
    void aDisabledAccountCannotReportAnything() {
        doThrow(new ResourceNotFoundException("Community content not found."))
                .when(access).requireActiveUser(REPORTER);

        assertThatThrownBy(() -> service.reportQuestion(QUESTION_ID,
                report(CommunityReportReason.SPAM, null), REPORTER))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(questions, reports);
    }

    // ----------------------------------------------------------------- queue

    @Test
    void theQueueNeedsAnAdministrator() {
        doThrow(new AccessDeniedException("This area is for administrators."))
                .when(access).requireAdministrator(REPORTER);

        assertThatThrownBy(() -> service.listOpenReports(REPORTER, 0, 10))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.countOpenReports(REPORTER))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(reports);
    }

    /**
     * A queue row for a report whose content is no longer public still carries the text and
     * the status.
     *
     * <p>This is the read model's whole purpose: the public detail page answers 404 once
     * content is hidden, so a moderator who could not read hidden content here could not
     * decide anything about it.</p>
     */
    @Test
    void aQueueRowCarriesHiddenContentInFull() {
        CommunityQuestion hidden = question();
        hidden.hide(NOW);
        when(reports.findByStatusOrderByCreatedAtAscIdAsc(eq(CommunityReportStatus.OPEN), any()))
                .thenReturn(pageOf(CommunityReport.ofQuestion(REPORTER, QUESTION_ID,
                        CommunityReportReason.ABUSIVE, "See rule 2.", NOW)));
        when(questions.findAllById(any())).thenReturn(List.of(hidden));

        Page<CommunityReportResponse> page = service.listOpenReports(ADMIN, 0, 10);

        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.targetBody()).isEqualTo(QUESTION_BODY);
            assertThat(row.targetStatus()).isEqualTo(CommunityContentStatus.HIDDEN);
            assertThat(row.targetAuthorId()).isEqualTo(AUTHOR);
            assertThat(row.questionTitle()).isEqualTo(QUESTION_TITLE);
            assertThat(row.detail()).isEqualTo("See rule 2.");
            assertThat(row.isTargetHidden()).isTrue();
            assertThat(row.isTargetVisible()).isFalse();
            // The thread page 404s for a hidden question unless the reader wrote it, and a
            // moderator did not - so the row must not offer a link to it.
            assertThat(row.isThreadPubliclyVisible()).isFalse();
            assertThat(row.isOpen()).isTrue();
        });
    }

    @Test
    void aQueueRowAboutAnAnswerNamesTheQuestionItBelongsTo() {
        when(reports.findByStatusOrderByCreatedAtAscIdAsc(eq(CommunityReportStatus.OPEN), any()))
                .thenReturn(pageOf(CommunityReport.ofAnswer(REPORTER, ANSWER_ID,
                        CommunityReportReason.OFF_TOPIC, null, NOW)));
        when(answers.findAllById(any())).thenReturn(List.of(answer()));
        when(questions.findAllById(any())).thenReturn(List.of(question()));

        Page<CommunityReportResponse> page = service.listOpenReports(ADMIN, 0, 10);

        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.targetType()).isEqualTo(CommunityContentType.ANSWER);
            assertThat(row.targetId()).isEqualTo(ANSWER_ID);
            // The thread is the answer's parent, not the answer itself.
            assertThat(row.questionId()).isEqualTo(QUESTION_ID);
            assertThat(row.questionTitle()).isEqualTo(QUESTION_TITLE);
            assertThat(row.targetBody()).isEqualTo(ANSWER_BODY);
            assertThat(row.targetAuthorId()).isEqualTo(AUTHOR);
            assertThat(row.isThreadPubliclyVisible()).isTrue();
            assertThat(row.isTargetVisible()).isTrue();
        });
    }

    // ------------------------------------------------------------ decisions

    @Test
    void aDecisionOnAnOpenReportIsRecordedWithItsModeratorAndTime() {
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(openReport()));
        when(reports.resolveIfOpen(REPORT_ID, CommunityReportStatus.ACTIONED, null, ADMIN, NOW))
                .thenReturn(1);

        assertThat(service.resolveReport(REPORT_ID,
                resolve(CommunityReportStatus.ACTIONED, null, false), ADMIN)).isTrue();
    }

    /**
     * The decision is written by one conditional statement, and the service acts on what
     * that statement affected rather than on what it asked for. A moderator who lost the
     * race - somebody settled the report between the page rendering and the submission -
     * changes nothing.
     */
    @Test
    void aReportSomebodyElseAlreadySettledChangesNothingAtAll() {
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(openReport()));
        when(reports.resolveIfOpen(any(), any(), any(), any(), any())).thenReturn(0);

        assertThat(service.resolveReport(REPORT_ID,
                resolve(CommunityReportStatus.ACTIONED, "handled", true), ADMIN)).isFalse();

        // The losing submission does not hide the content either. That is the failure this
        // branch exists to prevent: content taken down on the strength of a decision the
        // moderator did not make.
        verify(questions, never()).findByIdForUpdate(anyLong());
        verify(questions, never()).clearAcceptanceIfPresent(any(), any());
        verify(answers, never()).findById(anyLong());
        verifyNoInteractions(events);
    }

    @Test
    void upholdingAReportWithHideAskedForHidesTheQuestionAndAnnouncesIt() {
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(openReport()));
        when(reports.resolveIfOpen(any(), any(), any(), any(), any())).thenReturn(1);
        CommunityQuestion question = question();
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question));

        assertThat(service.resolveReport(REPORT_ID,
                resolve(CommunityReportStatus.ACTIONED, "  removed  ", true), ADMIN)).isTrue();

        // The moderator's note travels trimmed, as the entity stores a reporter's.
        verify(reports).resolveIfOpen(REPORT_ID, CommunityReportStatus.ACTIONED, "removed",
                ADMIN, NOW);
        assertThat(question.getStatus()).isEqualTo(CommunityContentStatus.HIDDEN);

        CommunityContentHiddenEvent event = capturedEvent(CommunityContentHiddenEvent.class);
        assertThat(event.contentId()).isEqualTo(QUESTION_ID);
        assertThat(event.contentType()).isEqualTo(CommunityContentType.QUESTION);
        assertThat(event.questionId()).isEqualTo(QUESTION_ID);
        assertThat(event.authorId()).isEqualTo(AUTHOR);
        assertThat(event.actorUserId()).isEqualTo(ADMIN);
        assertThat(event.resultingStatus()).isEqualTo(CommunityContentStatus.HIDDEN);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    /**
     * The two are independent on purpose: a report can be dismissed with the content hidden,
     * because the reporter was wrong about the reason and right that something was wrong.
     */
    @Test
    void dismissingAReportCanStillHideTheContent() {
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(openReport()));
        when(reports.resolveIfOpen(any(), any(), any(), any(), any())).thenReturn(1);
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question()));

        service.resolveReport(REPORT_ID,
                resolve(CommunityReportStatus.DISMISSED, "wrong reason", true), ADMIN);

        verify(reports).resolveIfOpen(REPORT_ID, CommunityReportStatus.DISMISSED,
                "wrong reason", ADMIN, NOW);
        verify(questions).findByIdForUpdate(QUESTION_ID);
        verify(events).publishEvent(any(CommunityContentHiddenEvent.class));
    }

    /** A blank note is stored as no note at all, exactly as a reporter's blank one is. */
    @Test
    void aBlankResolutionNoteIsStoredAsNoNote() {
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(openReport()));
        when(reports.resolveIfOpen(any(), any(), any(), any(), any())).thenReturn(1);

        service.resolveReport(REPORT_ID, resolve(CommunityReportStatus.DISMISSED, "   ", false),
                ADMIN);

        verify(reports).resolveIfOpen(REPORT_ID, CommunityReportStatus.DISMISSED, null, ADMIN, NOW);
        // Dismissing hides nothing, and the content was never read.
        verify(questions, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(events);
    }

    /**
     * {@code OPEN} is the absence of a decision, and a caller that submits it is refused
     * rather than mapped onto one of the two real outcomes.
     */
    @Test
    void openIsNotASubmittableDecision() {
        assertThatThrownBy(() -> service.resolveReport(REPORT_ID,
                resolve(CommunityReportStatus.OPEN, null, false), ADMIN))
                .isInstanceOf(InputValidationException.class)
                .hasMessageContaining("upheld or dismissed");

        assertThatThrownBy(() -> service.resolveReport(REPORT_ID,
                resolve(null, null, false), ADMIN))
                .isInstanceOf(InputValidationException.class);

        verifyNoInteractions(reports);
    }

    @Test
    void everyModerationMethodRequiresAnAdministrator() {
        doThrow(new AccessDeniedException("This area is for administrators."))
                .when(access).requireAdministrator(REPORTER);

        assertThatThrownBy(() -> service.resolveReport(REPORT_ID,
                resolve(CommunityReportStatus.ACTIONED, null, false), REPORTER))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.hideQuestion(QUESTION_ID, REPORTER))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.restoreQuestion(QUESTION_ID, REPORTER))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.hideAnswer(ANSWER_ID, REPORTER))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.restoreAnswer(ANSWER_ID, REPORTER))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(questions, answers, reports);
    }

    @Test
    void decidingAReportThatIsNotThereIsANotFound() {
        when(reports.findById(REPORT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveReport(REPORT_ID,
                resolve(CommunityReportStatus.ACTIONED, null, false), ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(events);
    }

    // ----------------------------------------------------------- governance

    /**
     * Hiding a question touches the question and nothing else: its answers are not
     * withdrawn, not hidden and not even read.
     */
    @Test
    void hidingAQuestionLeavesItsAnswersAlone() {
        CommunityQuestion question = question();
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question));

        assertThat(service.hideQuestion(QUESTION_ID, ADMIN)).isTrue();

        assertThat(question.getStatus()).isEqualTo(CommunityContentStatus.HIDDEN);
        assertThat(question.getAcceptedAnswerId()).isNull();
        verifyNoInteractions(answers);
        assertThat(capturedEvent(CommunityContentHiddenEvent.class).contentType())
                .isEqualTo(CommunityContentType.QUESTION);
    }

    @Test
    void hidingWhatIsAlreadyHiddenIsAQuietNoOp() {
        CommunityQuestion question = question();
        question.hide(NOW);
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question));

        assertThat(service.hideQuestion(QUESTION_ID, ADMIN)).isFalse();
        verifyNoInteractions(events);
    }

    @Test
    void restoringBringsBackOnlyWhatAModeratorHidAndAnnouncesNothing() {
        CommunityQuestion withdrawn = question();
        withdrawn.withdraw(NOW);
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(withdrawn));

        // An author's withdrawal is not a moderator's to undo.
        assertThat(service.restoreQuestion(QUESTION_ID, ADMIN)).isFalse();
        assertThat(withdrawn.getStatus()).isEqualTo(CommunityContentStatus.WITHDRAWN);

        CommunityQuestion hidden = question();
        hidden.hide(NOW);
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(hidden));

        assertThat(service.restoreQuestion(QUESTION_ID, ADMIN)).isTrue();
        assertThat(hidden.getStatus()).isEqualTo(CommunityContentStatus.VISIBLE);
        // No frozen event shape exists for a restore; see the class comment.
        verifyNoInteractions(events);
    }

    /**
     * Hiding the accepted answer clears the acceptance in the same transaction, in the lock
     * order phase 3 established: the question row is locked before the answer is read.
     *
     * <p>The order is the whole reason {@code findQuestionIdById} exists as a scalar query.
     * Reading the answer first would put it in the persistence context before the lock, and
     * the status the decision was made from could be one a concurrent transaction had
     * already changed - which is the interleaving
     * {@code CommunityModerationPostgresIT} reproduces against a real server.</p>
     */
    @Test
    void hidingTheAcceptedAnswerClearsTheAcceptanceUnderTheQuestionLock() {
        CommunityQuestion question = acceptedQuestion();
        when(answers.findQuestionIdById(ANSWER_ID)).thenReturn(Optional.of(QUESTION_ID));
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(answer()));

        assertThat(service.hideAnswer(ANSWER_ID, ADMIN)).isTrue();

        verify(questions).clearAcceptanceIfPresent(QUESTION_ID, NOW);

        CommunityContentHiddenEvent event = capturedEvent(CommunityContentHiddenEvent.class);
        assertThat(event.contentId()).isEqualTo(ANSWER_ID);
        assertThat(event.contentType()).isEqualTo(CommunityContentType.ANSWER);
        assertThat(event.questionId()).isEqualTo(QUESTION_ID);
        assertThat(event.authorId()).isEqualTo(AUTHOR);
    }

    @Test
    void hidingAnAnswerThatIsNotTheAcceptedOneClearsNothing() {
        when(answers.findQuestionIdById(ANSWER_ID)).thenReturn(Optional.of(QUESTION_ID));
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question()));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(answer()));

        assertThat(service.hideAnswer(ANSWER_ID, ADMIN)).isTrue();

        verify(questions, never()).clearAcceptanceIfPresent(any(), any());
        assertThat(capturedEvent(CommunityContentHiddenEvent.class).contentType())
                .isEqualTo(CommunityContentType.ANSWER);
    }

    /**
     * A withdrawn answer that is somehow still the accepted one - a state phase 3 also
     * cleans up after - has its acceptance cleared when a moderator hides it, even though
     * the status does not change.
     */
    @Test
    void hidingAnAlreadyWithdrawnAnswerStillClearsAStaleAcceptance() {
        CommunityQuestion question = acceptedQuestion();
        CommunityAnswer withdrawn = answer();
        withdrawn.withdraw(NOW);
        when(answers.findQuestionIdById(ANSWER_ID)).thenReturn(Optional.of(QUESTION_ID));
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(withdrawn));

        assertThat(service.hideAnswer(ANSWER_ID, ADMIN)).isFalse();

        verify(questions).clearAcceptanceIfPresent(QUESTION_ID, NOW);
        // Nothing changed, so nothing is announced.
        verifyNoInteractions(events);
    }

    /**
     * Restoring an answer publishes nothing, brings back only what a moderator hid, and does
     * not re-accept anything: acceptance is set by one statement and cleared by another, and
     * restoring content is neither of them.
     */
    @Test
    void restoringAnAnswerNeverReAcceptsItAndNeverRevivesAWithdrawal() {
        CommunityAnswer hidden = answer();
        hidden.hide(NOW);
        when(answers.findQuestionIdById(ANSWER_ID)).thenReturn(Optional.of(QUESTION_ID));
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(question()));
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(hidden));

        assertThat(service.restoreAnswer(ANSWER_ID, ADMIN)).isTrue();
        assertThat(hidden.getStatus()).isEqualTo(CommunityContentStatus.VISIBLE);

        CommunityAnswer withdrawn = answer();
        withdrawn.withdraw(NOW);
        when(answers.findById(ANSWER_ID)).thenReturn(Optional.of(withdrawn));

        assertThat(service.restoreAnswer(ANSWER_ID, ADMIN)).isFalse();
        assertThat(withdrawn.getStatus()).isEqualTo(CommunityContentStatus.WITHDRAWN);

        verify(questions, never()).clearAcceptanceIfPresent(any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void moderatingContentThatIsNotThereIsANotFound() {
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.hideQuestion(QUESTION_ID, ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);

        when(answers.findQuestionIdById(ANSWER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.hideAnswer(ANSWER_ID, ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.restoreAnswer(ANSWER_ID, ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);

        // A null id is a 404 rather than a null-pointer, on every route into these methods.
        assertThatThrownBy(() -> service.hideQuestion(null, ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.hideAnswer(null, ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.restoreQuestion(null, ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.reportQuestion(null,
                report(CommunityReportReason.SPAM, null), REPORTER))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.reportAnswer(null,
                report(CommunityReportReason.SPAM, null), REPORTER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------- fixtures

    private static final String QUESTION_TITLE = "Projector will not power on";
    private static final String QUESTION_BODY =
            "The projector in seminar room three shows no picture at all.";
    private static final String ANSWER_BODY =
            "Have you checked the power cable behind the desk?";

    /**
     * A stored, visible question belonging to {@link #AUTHOR}.
     *
     * <p>The id is written onto the field because the database assigns it in production and
     * a mocked repository never inserts anything - the same fixture, for the same reason, as
     * {@code CommunityAnswerServiceTest}.</p>
     */
    private static CommunityQuestion question() {
        CommunityQuestion question = CommunityQuestion.ask(AUTHOR, QUESTION_TITLE, QUESTION_BODY,
                CommunityCategory.HARDWARE, NOW);
        ReflectionTestUtils.setField(question, "id", QUESTION_ID);
        return question;
    }

    /**
     * The same question, in the state {@code acceptAnswerIfOpen} would leave it in.
     *
     * <p>This is the only way a test can reach that state: the entity deliberately has no
     * setter for the accepted answer, so no entity write path can change one. In production
     * the state is reached by the bulk statement, which a mocked repository does not run.</p>
     */
    private static CommunityQuestion acceptedQuestion() {
        CommunityQuestion question = question();
        ReflectionTestUtils.setField(question, "acceptedAnswerId", ANSWER_ID);
        return question;
    }

    /**
     * A stored, visible answer on {@link #QUESTION_ID}, belonging to {@link #AUTHOR}.
     *
     * <p>The id is written onto the field for the same reason as {@link #question()}: the
     * queue's row builder looks the reported content up by id, so a fixture without one
     * would be a row whose content is "missing" rather than one whose content is there.</p>
     */
    private static CommunityAnswer answer() {
        CommunityAnswer answer =
                CommunityAnswer.post(QUESTION_ID, AUTHOR, ANSWER_BODY, NOW.plusSeconds(60));
        ReflectionTestUtils.setField(answer, "id", ANSWER_ID);
        return answer;
    }

    private static CommunityReport openReport() {
        return CommunityReport.ofQuestion(REPORTER, QUESTION_ID, CommunityReportReason.SPAM,
                null, NOW);
    }

    private static <T> Page<T> pageOf(T row) {
        return new PageImpl<>(List.of(row), Pageable.ofSize(10), 1);
    }

    private static ReportContentCommand report(CommunityReportReason reason, String detail) {
        ReportContentCommand command = new ReportContentCommand();
        command.setReason(reason);
        command.setDetail(detail);
        return command;
    }

    private static ResolveReportCommand resolve(
            CommunityReportStatus decision, String note, boolean hideContent) {
        ResolveReportCommand command = new ResolveReportCommand();
        command.setDecision(decision);
        command.setNote(note);
        command.setHideContent(hideContent);
        return command;
    }

    /**
     * A report with an id, so the value the service returns can be asserted on. The column
     * belongs to the database, so the test writes the field the way Hibernate would.
     */
    private static CommunityReport withId(CommunityReport report) {
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        return report;
    }

    private CommunityReport capturedReport() {
        ArgumentCaptor<CommunityReport> saved = ArgumentCaptor.forClass(CommunityReport.class);
        verify(reports).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    private <T> T capturedEvent(Class<T> type) {
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        return type.cast(published.getValue());
    }
}
