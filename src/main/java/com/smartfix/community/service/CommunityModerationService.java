package com.smartfix.community.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.community.config.CommunityProperties;
import com.smartfix.community.domain.CommunityAnswer;
import com.smartfix.community.domain.CommunityContentStatus;
import com.smartfix.community.domain.CommunityContentType;
import com.smartfix.community.domain.CommunityQuestion;
import com.smartfix.community.domain.CommunityReport;
import com.smartfix.community.domain.CommunityReportStatus;
import com.smartfix.community.dto.CommunityReportResponse;
import com.smartfix.community.dto.ReportContentCommand;
import com.smartfix.community.dto.ResolveReportCommand;
import com.smartfix.community.dto.ReportView;
import com.smartfix.audit.service.AuditService;
import com.smartfix.community.event.CommunityContentHiddenEvent;
import com.smartfix.community.repository.CommunityAnswerRepository;
import com.smartfix.community.repository.CommunityQuestionRepository;
import com.smartfix.community.repository.CommunityReportRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reporting and moderation: the write side of the community board that is not the
 * author's own.
 *
 * <h2>Who may do what, and where that is decided</h2>
 *
 * <p>Reporting is open to every ACTIVE account, because any reader who can see a piece
 * of content can see that something is wrong with it. Everything else - the queue, the
 * decisions, hiding and restoring - is for administrators, and every one of those
 * methods calls {@link CommunityAccessGuard#requireAdministrator} itself rather than
 * relying on {@code SecurityConfig}'s {@code /admin/**} rule. The route rule is the
 * outer boundary and stays; this check is what keeps the rule true for a caller that
 * did not arrive through a route.</p>
 *
 * <h2>What a moderator may not do</h2>
 *
 * <p>Nothing here rewrites anybody's words. There is no edit, no delete, and no way to
 * accept or un-accept an answer on somebody else's behalf: a hidden question keeps its
 * author, its text and its answers, and the only things a moderation call can change
 * are the two status columns and, when the accepted answer itself is hidden, the
 * acceptance that can no longer be honoured. The brief draws that line and this class
 * stays behind it.</p>
 *
 * <h2>Idempotence, and the one thing that must never happen</h2>
 *
 * <p>Resolving a report twice, hiding what is already hidden and restoring what is
 * already visible all leave the same state behind and raise no second event, so a
 * double-submitted form is a no-op rather than an error page or a duplicate
 * notification.</p>
 *
 * <p>The thing that must never happen is a settled decision being overwritten. Two
 * moderators opening the same report and both submitting is not a rare race; it is
 * what two people looking at one queue do. The decision is therefore written by a
 * single conditional statement whose affected-row count decides everything:
 * {@code resolveIfOpen} records a decision only while the report is still
 * {@code OPEN}, and the caller that loses the race changes nothing at all - it does
 * not hide the content either, because hiding is applied only by the call that
 * actually settled the report. The result is one decision, recorded once, with the
 * moderator and time of the person who made it.</p>
 *
 * <h2>One lock, taken first, and the question row it is always on</h2>
 *
 * <p>Hiding and restoring reuse the phase 3 lock order exactly: every moderation write
 * takes a write lock on the <em>question</em> row through
 * {@code CommunityQuestionRepository.findByIdForUpdate} before it reads or writes
 * anything else, and nothing else in the module locks an answer row. A transaction
 * therefore holds at most one such lock, so no ordering between two of them exists to
 * get wrong. This is what makes "accept an answer" and "hide that same answer"
 * serialise instead of interleaving: without it, an acceptance could be decided from a
 * status that a moderator had already changed.</p>
 *
 * <p>Hiding the accepted answer clears the acceptance in the same transaction, because
 * a question that reads as solved with a hidden answer beneath it is the state the
 * derived-solved-state rule exists to prevent. (Hiding a <em>question</em> clears
 * nothing: its answers are untouched, and the acceptance is still the author's choice
 * when the question comes back.) The clearing statement is conditional, so it cannot
 * remove an acceptance that a concurrent operation has already replaced.</p>
 *
 * <h2>Events</h2>
 *
 * <p>Hiding publishes CommunityContentHiddenEvent for AFTER_COMMIT notification.
 * Hide, restore and report decisions also append real audit entries inside the
 * business transaction, so they commit or roll back together. Restore needs no
 * new public event shape. See ADR-003.</p>
 */
@Service
@Transactional
public class CommunityModerationService {

    /** The single refusal shared with the rest of the module, for content that is not there. */
    private static final String NOT_FOUND = "Community content not found.";

    /**
     * Raised when an account reports the same thing twice.
     *
     * <p>One sentence for both cases - still waiting, or already settled - because the
     * reporter's next action is the same either way (nothing), and because a sentence
     * that distinguished them would tell a reporter what a moderator decided about
     * their report, which the moderation queue is where that is said.</p>
     */
    private static final String ALREADY_REPORTED =
            "You have already reported this. A report can only be filed once.";

    /** Raised when a submitted decision is not one of the two decisions a moderator may take. */
    private static final String DECISION_REQUIRED =
            "A report can only be upheld or dismissed.";

    private final CommunityQuestionRepository questions;
    private final CommunityAnswerRepository answers;
    private final CommunityReportRepository reports;
    private final CommunityAccessGuard access;
    private final CommunityProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AuditService audit;

    public CommunityModerationService(
            CommunityQuestionRepository questions,
            CommunityAnswerRepository answers,
            CommunityReportRepository reports,
            CommunityAccessGuard access,
            CommunityProperties properties,
            ApplicationEventPublisher events,
            Clock clock,
            AuditService audit) {
        this.questions = questions;
        this.answers = answers;
        this.reports = reports;
        this.access = access;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ reporting

    /**
     * Reports a question on behalf of the authenticated account.
     *
     * <p>Only publicly visible content can be reported. A hidden or withdrawn question
     * answers 404 rather than a conflict, for the reason every refusal in this module
     * answers 404: a conflict would confirm that the id names something, and a reader
     * has no business learning that from a report form.</p>
     *
     * <p>The duplicate check runs before the insert so that the ordinary double-click
     * gets a sentence instead of a database error, and the insert is flushed inside a
     * try so that the same collision arriving by two simultaneous requests is answered
     * the same way. The check cannot be trusted under concurrency - it is a read
     * followed by a write - which is exactly why {@code V19}'s unique index exists and
     * why this catch is not dead code.</p>
     *
     * @return the new report's id
     * @throws ResourceNotFoundException when there is no such question, or it is not public
     * @throws BusinessConflictException when this account has already reported it
     */
    public Long reportQuestion(Long questionId, ReportContentCommand command, Long actorUserId) {
        access.requireActiveUser(actorUserId);
        CommunityQuestion question = requirePublicQuestion(questionId);

        if (reports.existsByReporterIdAndQuestionId(actorUserId, question.getId())) {
            throw new BusinessConflictException(ALREADY_REPORTED);
        }
        CommunityReport report = CommunityReport.ofQuestion(
                actorUserId,
                question.getId(),
                command.getReason(),
                command.getDetail(),
                clock.instant());
        return saveReport(report);
    }

    /**
     * Reports an answer on behalf of the authenticated account.
     *
     * <p>Both the answer and the question it belongs to must be publicly visible. The
     * answer's own visibility is not enough: an answer to a hidden question is
     * unreachable from every public page, and a report about content nobody can read is
     * not a report a moderator can act on.</p>
     *
     * @return the new report's id
     * @throws ResourceNotFoundException when there is no such answer, or either the
     *         answer or its question is not public
     * @throws BusinessConflictException when this account has already reported it
     */
    public Long reportAnswer(Long answerId, ReportContentCommand command, Long actorUserId) {
        access.requireActiveUser(actorUserId);
        if (answerId == null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        CommunityAnswer answer = answers.findById(answerId)
                .filter(CommunityAnswer::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        requirePublicQuestion(answer.getQuestionId());

        if (reports.existsByReporterIdAndAnswerId(actorUserId, answerId)) {
            throw new BusinessConflictException(ALREADY_REPORTED);
        }
        CommunityReport report = CommunityReport.ofAnswer(
                actorUserId,
                answerId,
                command.getReason(),
                command.getDetail(),
                clock.instant());
        return saveReport(report);
    }

    /**
     * Inserts a report, translating a lost race into the same refusal the check gave.
     *
     * <p>Flushed here rather than at commit so the collision is caught while this method
     * can still translate it. The service has already validated everything else, so a
     * constraint violation on this insert can realistically only be the duplicate
     * report - the same reasoning {@code UserService.createAccount} applies to a
     * duplicate username.</p>
     */
    private Long saveReport(CommunityReport report) {
        try {
            return reports.saveAndFlush(report).getId();
        } catch (DataIntegrityViolationException raced) {
            throw new BusinessConflictException(ALREADY_REPORTED);
        }
    }

    // -------------------------------------------------------------------- queue

    /**
     * One page of the reports still waiting, oldest first.
     *
     * <p>The reported content is loaded in two queries for the whole page rather than
     * two per row, and it is loaded <em>without</em> a visibility predicate: judging a
     * report means reading content that is no longer public, and a moderator is the one
     * reader for whom plan section 6.8 says a hidden item is visible. Nothing here
     * changes what the public pages return - this is a separate read model, reached only
     * through the administrator check above it.</p>
     */
    public Page<CommunityReportResponse> listOpenReports(Long actorUserId, int page, int size) {
        access.requireAdministrator(actorUserId);

        Page<CommunityReport> found = reports.findByStatusOrderByCreatedAtAscIdAsc(
                CommunityReportStatus.OPEN, pageRequest(page, size));
        Map<Long, CommunityAnswer> answerRows = loadAnswers(found);
        Map<Long, CommunityQuestion> questionRows = loadQuestions(found, answerRows);
        return found.map(report -> toResponse(report, questionRows, answerRows));
    }

    /** How many reports are waiting. Read by the moderation page's own summary. */
    public long countOpenReports(Long actorUserId) {
        access.requireAdministrator(actorUserId);
        return reports.countByStatus(CommunityReportStatus.OPEN);
    }

    /** Settled reports remain readable and retain their content restoration controls. */
    public Page<CommunityReportResponse> listReports(Long actorUserId, ReportView view, int page, int size) {
        if (view == null || view == ReportView.OPEN) {
            return listOpenReports(actorUserId, page, size);
        }
        access.requireAdministrator(actorUserId);
        var statuses = view == ReportView.HANDLED
                ? java.util.List.of(CommunityReportStatus.ACTIONED, CommunityReportStatus.DISMISSED)
                : java.util.List.of(CommunityReportStatus.values());
        var found = reports.findByStatusInOrderByCreatedAtDescIdDesc(statuses, pageRequest(page, size));
        var answerRows = loadAnswers(found);
        var questionRows = loadQuestions(found, answerRows);
        return found.map(report -> toResponse(report, questionRows, answerRows));
    }

    // ------------------------------------------------------------------ decisions

    /**
     * Records a moderator's decision on one report, and hides the content if asked.
     *
     * <h2>The order of the two writes, and why it is this way round</h2>
     *
     * <p>The decision is written first, conditionally, and the content is hidden only
     * if that write affected a row. Doing it the other way round would let a moderator
     * who lost the race hide content on the strength of a decision they did not make:
     * the second submission would see a report it believed was open, hide the content,
     * and only then discover that somebody else had already dismissed the report and
     * left the content up. Nothing below the losing branch runs, so a lost race changes
     * nothing at all.</p>
     *
     * <p>Both writes are in one transaction, so a hiding that fails for any reason rolls
     * the decision back with it, and the event a successful hide publishes is delivered
     * only on commit.</p>
     *
     * @return whether this call recorded the decision - {@code false} when another
     *         moderator had already settled the report, in which case nothing changed
     * @throws ResourceNotFoundException when there is no such report
     * @throws InputValidationException when the submitted decision is not one a
     *         moderator may take
     */
    public boolean resolveReport(Long reportId, ResolveReportCommand command, Long actorUserId) {
        access.requireAdministrator(actorUserId);
        CommunityReportStatus decision = command.getDecision();
        if (decision == null || decision.isOpen()) {
            throw new InputValidationException(DECISION_REQUIRED);
        }
        if (reportId == null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        CommunityReport report = reports.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));

        // Read before the conditional update, which clears the persistence context.
        boolean aboutQuestion = report.targetsQuestion();
        Long targetId = report.getTargetId();
        Instant now = clock.instant();

        int affected = reports.resolveIfOpen(
                reportId, decision, blankAsNull(command.getNote()), actorUserId, now);
        if (affected == 0) {
            return false;
        }

        if (command.isHideContent()) {
            if (aboutQuestion) {
                hideQuestionAsModerator(targetId, actorUserId, now);
            } else {
                hideAnswerAsModerator(targetId, actorUserId, now);
            }
        }
        audit.record(actorUserId, "REPORT_RESOLVED", "REPORT", reportId, decision.name(), now);
        return true;
    }

    // ------------------------------------------------------------------ governance

    /**
     * Hides a question at a moderator's decision.
     *
     * <p>Its answers are not touched, visible ones included; see
     * {@link CommunityQuestion#hide}. Idempotent: hiding what is already hidden, or what
     * its author has already withdrawn, changes nothing and publishes nothing - so the
     * double-click and the redundant moderation action are both quiet no-ops rather than
     * a second notification.</p>
     *
     * @return whether this call changed the status
     * @throws com.smartfix.common.exception.ResourceNotFoundException when there is no
     *         such question
     */
    public boolean hideQuestion(Long questionId, Long actorUserId) {
        access.requireAdministrator(actorUserId);
        return hideQuestionAsModerator(questionId, actorUserId, clock.instant());
    }

    /**
     * Returns a hidden question to public view.
     *
     * <p>Only a question a moderator hid comes back; one its author withdrew stays
     * withdrawn, and its acceptance - if it had one - is still recorded, because hiding
     * a question never cleared it.</p>
     *
     * @return whether this call changed the status
     */
    public boolean restoreQuestion(Long questionId, Long actorUserId) {
        access.requireAdministrator(actorUserId);
        CommunityQuestion question = lockQuestion(questionId);
        Instant now = clock.instant();
        boolean changed = question.restore(now);
        if (changed) {
            audit.record(actorUserId, "CONTENT_RESTORED", "QUESTION", questionId, "VISIBLE", now);
        }
        return changed;
    }

    /**
     * Hides an answer at a moderator's decision.
     *
     * <p>If it is the accepted answer, the acceptance is cleared in the same
     * transaction, following the lock order phase 3 established: the question row is
     * locked first, the answer is read afterwards, and both changes commit together or
     * not at all.</p>
     *
     * @return whether this call changed the status
     * @throws ResourceNotFoundException when there is no such answer
     */
    public boolean hideAnswer(Long answerId, Long actorUserId) {
        access.requireAdministrator(actorUserId);
        return hideAnswerAsModerator(answerId, actorUserId, clock.instant());
    }

    /**
     * Returns a hidden answer to public view.
     *
     * <p>It does not come back accepted. Acceptance is set by one statement and cleared
     * by another, and restoring content is neither of them - so a question whose
     * accepted answer was hidden and later restored is a question with a visible answer
     * and no acceptance, which is what each of those two actions individually said. An
     * answer its author withdrew is not restored either.</p>
     *
     * @return whether this call changed the status
     */
    public boolean restoreAnswer(Long answerId, Long actorUserId) {
        access.requireAdministrator(actorUserId);
        if (answerId == null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        Long questionId = answers.findQuestionIdById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        lockQuestion(questionId);
        CommunityAnswer answer = answers.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        Instant now = clock.instant();
        boolean changed = answer.restore(now);
        if (changed) {
            audit.record(actorUserId, "CONTENT_RESTORED", "ANSWER", answerId, "VISIBLE", now);
        }
        return changed;
    }

    // ------------------------------------------------------------------- internals

    private boolean hideQuestionAsModerator(Long questionId, Long actorUserId, Instant now) {
        CommunityQuestion question = lockQuestion(questionId);
        if (!question.hide(now)) {
            return false;
        }
        audit.record(actorUserId, "CONTENT_HIDDEN", "QUESTION", questionId, "HIDDEN", now);
        events.publishEvent(new CommunityContentHiddenEvent(
                question.getId(),
                CommunityContentType.QUESTION,
                // A question is its own thread: the id of the content and the id of the
                // question it belongs to are the same value here, which is what every
                // consumer of this event expects.
                question.getId(),
                question.getAuthorId(),
                actorUserId,
                CommunityContentStatus.HIDDEN,
                now));
        return true;
    }

    private boolean hideAnswerAsModerator(Long answerId, Long actorUserId, Instant now) {
        if (answerId == null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        // The answer id is what the route names, so the question to lock is discovered
        // first - through a scalar query, so the answer itself is not loaded into the
        // persistence context and the read below really happens after the lock.
        Long questionId = answers.findQuestionIdById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        CommunityQuestion question = lockQuestion(questionId);
        CommunityAnswer answer = answers.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));

        boolean changed = answer.hide(now);

        // Cleared whenever the question still points at this answer, whether or not the
        // status changed in this call. Mirroring CommunityAnswerService.withdraw: a
        // hidden answer must not stay the accepted one, and the statement is conditional
        // so it cannot clear a newer acceptance.
        if (answerId.equals(question.getAcceptedAnswerId())) {
            questions.clearAcceptanceIfPresent(questionId, now);
        }

        if (changed) {
            audit.record(actorUserId, "CONTENT_HIDDEN", "ANSWER", answerId, "HIDDEN", now);
            events.publishEvent(new CommunityContentHiddenEvent(
                    answerId,
                    CommunityContentType.ANSWER,
                    questionId,
                    answer.getAuthorId(),
                    actorUserId,
                    CommunityContentStatus.HIDDEN,
                    now));
        }
        return changed;
    }

    private CommunityQuestion lockQuestion(Long questionId) {
        if (questionId == null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return questions.findByIdForUpdate(questionId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private CommunityQuestion requirePublicQuestion(Long questionId) {
        if (questionId == null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return questions.findById(questionId)
                .filter(CommunityQuestion::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /** The reported answers behind one page, in one query rather than one per row. */
    private Map<Long, CommunityAnswer> loadAnswers(Page<CommunityReport> page) {
        Set<Long> ids = new LinkedHashSet<>();
        for (CommunityReport report : page.getContent()) {
            if (!report.targetsQuestion()) {
                ids.add(report.getAnswerId());
            }
        }
        Map<Long, CommunityAnswer> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            for (CommunityAnswer answer : answers.findAllById(ids)) {
                byId.put(answer.getId(), answer);
            }
        }
        return byId;
    }

    /**
     * The questions behind one page, in one query rather than one per row.
     *
     * <p>Two kinds are needed: the questions that were reported, and the parents of the
     * answers that were reported - an answer report shows its thread's title, and
     * whether that thread may be linked to is decided from the thread's own status. Both
     * kinds are collected before the query runs, so the page costs two reads in total
     * however many rows it holds.</p>
     */
    private Map<Long, CommunityQuestion> loadQuestions(
            Page<CommunityReport> page, Map<Long, CommunityAnswer> answerRows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (CommunityReport report : page.getContent()) {
            if (report.targetsQuestion()) {
                ids.add(report.getQuestionId());
            } else {
                CommunityAnswer answer = answerRows.get(report.getAnswerId());
                if (answer != null) {
                    ids.add(answer.getQuestionId());
                }
            }
        }
        Map<Long, CommunityQuestion> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            for (CommunityQuestion question : questions.findAllById(ids)) {
                byId.put(question.getId(), question);
            }
        }
        return byId;
    }

    /**
     * Builds one queue row from the report and the content it is about.
     *
     * <p>Nothing here filters on status. The statuses travel as data so the page can
     * label the content and offer the one governance action that applies to it - and the
     * body travels in full, because the moderator is being asked to judge that text and
     * cannot open it on the public page once it is hidden.</p>
     */
    private CommunityReportResponse toResponse(
            CommunityReport report,
            Map<Long, CommunityQuestion> questionRows,
            Map<Long, CommunityAnswer> answerRows) {

        CommunityContentType targetType = report.getTargetType();
        CommunityContentStatus targetStatus = null;
        Long targetAuthorId = null;
        String targetBody = null;
        Long threadId = null;
        String threadTitle = null;
        CommunityContentStatus threadStatus = null;

        if (report.targetsQuestion()) {
            CommunityQuestion question = questionRows.get(report.getQuestionId());
            if (question != null) {
                targetStatus = question.getStatus();
                targetAuthorId = question.getAuthorId();
                targetBody = question.getBody();
                threadId = question.getId();
                threadTitle = question.getTitle();
                threadStatus = question.getStatus();
            }
        } else {
            CommunityAnswer answer = answerRows.get(report.getAnswerId());
            if (answer != null) {
                targetStatus = answer.getStatus();
                targetAuthorId = answer.getAuthorId();
                targetBody = answer.getBody();
                CommunityQuestion parent = questionRows.get(answer.getQuestionId());
                if (parent != null) {
                    threadId = parent.getId();
                    threadTitle = parent.getTitle();
                    threadStatus = parent.getStatus();
                }
            }
        }

        return new CommunityReportResponse(
                report.getId(),
                targetType,
                report.getTargetId(),
                threadId,
                threadTitle,
                threadStatus,
                targetBody,
                targetStatus,
                targetAuthorId,
                report.getReason(),
                report.getDetail(),
                report.getReporterId(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getHandledByUserId(),
                report.getHandledAt(),
                report.getResolutionNote());
    }

    /** Reads a blank note as no note, matching what the entity stores for the reporter's own. */
    private static String blankAsNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Paging for the queue: oldest first, with the same default and ceiling as every
     * other list in the module.
     *
     * <p>No {@code Sort} is passed. The order is in the repository method's name so
     * that it cannot be dropped by a caller, and a {@code Sort} here would be a second
     * statement of the same rule that could disagree with the first.</p>
     */
    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), properties.getPage().resolveSize(size));
    }
}
