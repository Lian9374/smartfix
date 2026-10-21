package com.smartfix.community;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.community.domain.CommunityAnswer;
import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityContentStatus;
import com.smartfix.community.domain.CommunityContentType;
import com.smartfix.community.domain.CommunityQuestion;
import com.smartfix.community.domain.CommunityReport;
import com.smartfix.community.domain.CommunityReportReason;
import com.smartfix.community.domain.CommunityReportStatus;
import com.smartfix.community.dto.ReportContentCommand;
import com.smartfix.community.dto.ResolveReportCommand;
import com.smartfix.community.event.CommunityContentHiddenEvent;
import com.smartfix.community.repository.CommunityAnswerRepository;
import com.smartfix.community.repository.CommunityQuestionRepository;
import com.smartfix.community.repository.CommunityReportRepository;
import com.smartfix.community.service.CommunityAnswerService;
import com.smartfix.community.service.CommunityModerationService;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.CreateUserCommand;
import com.smartfix.user.service.UserService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reporting and moderation against a real PostgreSQL server: the races and the constraints
 * that H2 cannot decide.
 *
 * <h2>Why the other tests are not enough</h2>
 *
 * <p>{@code CommunityModerationServiceTest} verifies call order with mocks, which have no
 * rows to affect and no second connection to contend with - an affected-row count there is
 * whatever the stub was told to return. {@code CommunityModerationPagesIT} drives the real
 * controllers over H2, whose lock manager and {@code SELECT ... FOR UPDATE} are not
 * PostgreSQL's, and whose schema is built from the entities rather than from {@code V19} -
 * so the partial unique indexes and the {@code CHECK} constraints do not exist in it at
 * all. Everything below is a statement about two transactions competing for one row, or
 * about a constraint only the migration declares, so the only honest place to check it is
 * a real server.</p>
 *
 * <h2>What is checked</h2>
 *
 * <ul>
 *   <li>two moderators deciding the same report at once: one decision, recorded once, and
 *       the loser changing nothing - including not hiding the content;</li>
 *   <li>an acceptance racing a hide of the same answer: the phase 3 lock order holds, and
 *       no question is left pointing at an answer nobody can read;</li>
 *   <li>two simultaneous reports of the same thing by the same account: one row and one
 *       refusal, which is what {@code V19}'s partial unique index is for;</li>
 *   <li>every constraint {@code V19} declares, exercised by writing the row it forbids;</li>
 *   <li>the event contract: a hide announces itself at commit, and a transaction that rolls
 *       back announces nothing; real notifications and atomic moderation audit use the
 *       V20/V21 production tables, not a test audit substitute;</li>
 *   <li>that none of it creates request-side business rows - the brief's "community
 *       operations must not create maintenance requests, work orders, assignments or SLA
 *       data". Whether assignment and SLA tables exist at all is not A's to decide; what
 *       exists is asserted on, and what does not is named below rather than pretended
 *       about.</li>
 * </ul>
 *
 * <h2>Opt-in, and self-contained</h2>
 *
 * <p>Listed in the {@code maven-failsafe-plugin} excludes and run only under the {@code
 * postgres-it} profile with {@code TEST_DB_URL}, {@code TEST_DB_USERNAME} and {@code
 * TEST_DB_PASSWORD} set. It needs a real server, so an environment without one reports it
 * as not run rather than as passed. Everything it writes lives in a randomly named schema
 * that is dropped afterwards.</p>
 */
@SpringBootTest(
        properties = {
            "spring.datasource.driver-class-name=org.postgresql.Driver",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            // Two racers each hold a connection for the length of their transaction, and
            // one of them holds it while the other blocks. A pool smaller than the number
            // of racers would deadlock the test pool, not the row lock.
            "spring.datasource.hikari.maximum-pool-size=8",
            "smartfix.bootstrap-admin.enabled=false"
        })
@Import(CommunityModerationPostgresIT.RecordedEvents.class)
class CommunityModerationPostgresIT {

    /** Synthetic test-only credential, never an application default. */
    private static final String PASSWORD = "TestPassword9";

    private static final Instant BASE = Instant.parse("2026-10-01T08:00:00Z");

    private static final String SCHEMA =
            "smartfix_moderation_race_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        String url = required("TEST_DB_URL");
        String user = required("TEST_DB_USERNAME");
        String password = required("TEST_DB_PASSWORD");
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement sql = connection.createStatement()) {
            assertThat(connection.getCatalog()).endsWith("_test");
            sql.execute("CREATE SCHEMA " + SCHEMA);
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot prepare a dedicated test schema", failure);
        }
        registry.add("spring.datasource.url",
                () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @Autowired private CommunityModerationService moderation;
    @Autowired private CommunityAnswerService answersTo;
    @Autowired private CommunityQuestionRepository questions;
    @Autowired private CommunityAnswerRepository answers;
    @Autowired private CommunityReportRepository reports;
    @Autowired private UserService users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private HiddenRecorder recorder;

    @BeforeEach
    void emptyTheSchema() {
        // The schema outlives a single test method, so each one starts from nothing and can
        // create its accounts under plain names.
        recorder.clear();
        jdbc.update("DELETE FROM audit_entries");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM community_reports");
        jdbc.update("DELETE FROM community_answers");
        jdbc.update("DELETE FROM community_questions");
        jdbc.update("DELETE FROM users");
    }

    // ------------------------------------------------- two moderators deciding

    /**
     * Two moderators decide the same report at the same instant, one asking for the content
     * to be hidden as well and one not.
     *
     * <p>What must not happen is both decisions being recorded, or the loser overwriting the
     * winner's - and the failure this test exists to catch is subtler than that: the loser
     * hiding the content on the strength of a decision it did not make. Hiding is applied
     * only by the call that affected a row, so the state below follows from which call won
     * and from nothing else.</p>
     *
     * <p>{@code resolveIfOpen}'s {@code WHERE status = 'OPEN'} is what makes this work under
     * PostgreSQL's {@code READ COMMITTED}: the second {@code UPDATE} blocks on the row lock,
     * re-evaluates its condition once the first commits, and matches nothing.</p>
     */
    @Test
    void twoModeratorsDecidingAtOnceLeaveOneDecisionAndOneUnchangedLoser() throws Exception {
        Long author = account("race.author", Role.REQUESTER);
        Long reporter = account("race.reporter", Role.REQUESTER);
        Long hidingModerator = account("race.moderator.a", Role.ADMINISTRATOR);
        Long dismissingModerator = account("race.moderator.b", Role.ADMINISTRATOR);

        Long question = openQuestion(author, "Two moderators, one report");
        Long report = openReport(reporter, question);

        List<Outcome<Boolean>> outcomes = together(List.of(
                deciding(report, hidingModerator, CommunityReportStatus.ACTIONED, "Taken down.", true),
                deciding(report, dismissingModerator, CommunityReportStatus.DISMISSED, "Left up.", false)));

        Outcome<Boolean> hideAsked = outcomes.get(0);
        Outcome<Boolean> noHideAsked = outcomes.get(1);

        assertThat(hideAsked.failure()).as("a lost race is a result, not an exception").isNull();
        assertThat(noHideAsked.failure()).as("a lost race is a result, not an exception").isNull();

        assertThat(hideAsked.value())
                .as("exactly one of the two submissions settled the report")
                .isNotEqualTo(noHideAsked.value());

        boolean hiddenAsWell = hideAsked.value();

        // One decision, and it is the winner's - status, note, moderator and time together.
        assertThat(statusOfReport(report))
                .isEqualTo(hiddenAsWell ? "ACTIONED" : "DISMISSED");
        assertThat(noteOfReport(report)).isEqualTo(hiddenAsWell ? "Taken down." : "Left up.");
        assertThat(handlerOfReport(report))
                .isEqualTo(hiddenAsWell ? hidingModerator : dismissingModerator);
        assertThat(handledAtOfReport(report)).isNotNull();

        // And the content is hidden if and only if the submission that asked for it won.
        assertThat(statusOfQuestion(question)).isEqualTo(hiddenAsWell ? "HIDDEN" : "VISIBLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_entries WHERE action = 'REPORT_RESOLVED' AND target_id = ?",
                Integer.class, report)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM audit_entries WHERE action = 'REPORT_RESOLVED' AND target_id = ?",
                Long.class, report)).isEqualTo(hiddenAsWell ? hidingModerator : dismissingModerator);
    }

    // -------------------------------------------------- acceptance vs hiding

    /**
     * A question's author accepts an answer while a moderator hides that same answer.
     *
     * <p>Without the shared lock order this interleaving is reachable: the acceptance reads
     * the answer as {@code VISIBLE}, the hide commits, and the acceptance is written anyway -
     * leaving a question that reads as solved with an answer nobody can read. Phase 3 made
     * every community write take a write lock on the <em>question</em> row first, and the
     * moderation write does the same, so the two serialise instead.</p>
     *
     * <p>Both orders are legal and which one happens is the database's decision. What is
     * asserted is that they leave the same state either way:</p>
     *
     * <ul>
     *   <li>the answer ends up hidden - hiding it is a moderator's decision and nothing in
     *       this race refuses it;</li>
     *   <li>the question ends up with no accepted answer, whether the acceptance was
     *       refused because the answer was already out of view, or succeeded and was
     *       cleared in the same transaction as the hide;</li>
     *   <li>no question is left pointing at an answer that is not publicly visible, which is
     *       the invariant the lock exists for.</li>
     * </ul>
     */
    @Test
    void anAcceptanceRacingAHideNeverLeavesANonVisibleAnswerAccepted() throws Exception {
        Long asker = account("race.asker", Role.REQUESTER);
        Long answerer = account("race.answerer", Role.TECHNICIAN);
        Long moderator = account("race.moderator", Role.ADMINISTRATOR);

        Long question = openQuestion(asker, "An acceptance racing a hide");
        Long answer = answer(question, answerer, "An answer about to be hidden by a moderator.");

        List<Outcome<Boolean>> outcomes = together(List.of(
                accepting(question, answer, asker),
                hiding(answer, moderator)));

        Outcome<Boolean> acceptance = outcomes.get(0);
        Outcome<Boolean> hide = outcomes.get(1);

        assertThat(hide.value())
                .as("hiding content a moderator has decided against is refused by nothing here")
                .isTrue();
        if (!acceptance.succeeded()) {
            // The hide reached the question row first, so by the time the acceptance looked,
            // the answer was no longer VISIBLE. That is the visibility conflict and not the
            // already-accepted one - this question had no acceptance to begin with.
            assertThat(acceptance.failure()).isInstanceOf(BusinessConflictException.class);
        }

        assertThat(statusOfAnswer(answer)).isEqualTo("HIDDEN");
        assertThat(acceptedAnswerIdOf(question))
                .as("hiding the accepted answer opens the question again")
                .isNull();
        assertThat(questionsPointingAtContentNobodyCanRead())
                .as("the invariant the shared lock order exists for")
                .isZero();
    }

    // ---------------------------------------------------- duplicate reporting

    /**
     * The same account reports the same question from two requests at once.
     *
     * <p>The service's pre-check is a read followed by a write and the two are not atomic
     * with respect to each other, so both requests pass it - which is exactly why
     * {@code V19}'s unique index exists and why the service's insert is flushed inside a
     * try. The outcome must be one row and one {@code BusinessConflictException}, with the
     * losing request getting the same readable sentence the check would have given rather
     * than an error page.</p>
     */
    @Test
    void twoSimultaneousReportsOfTheSameQuestionLeaveOneRowAndOneRefusal() throws Exception {
        Long author = account("race.author", Role.REQUESTER);
        Long reporter = account("race.reporter", Role.REQUESTER);
        Long question = openQuestion(author, "Two simultaneous reports");

        List<Outcome<Long>> outcomes = together(List.of(
                reportingQuestion(question, reporter, "The first request."),
                reportingQuestion(question, reporter, "The second request.")));

        assertThat(outcomes.stream().filter(Outcome::succeeded).count())
                .as("exactly one report may be stored")
                .isEqualTo(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.succeeded())
                        .map(Outcome::failure)
                        .toList())
                .as("the losing request is refused the way the friendly check refuses it")
                .singleElement()
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        refusal -> assertThat(refusal).hasMessageContaining("already reported"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM community_reports WHERE reporter_id = ? AND question_id = ?",
                Integer.class, reporter, question))
                .isEqualTo(1);
    }

    // ---------------------------------------------------------- V19's rules

    /**
     * Every constraint {@code V19} declares, exercised by writing the row it forbids.
     *
     * <p>These rows cannot be reached through any service method - the entity's own
     * factories and the service's checks refuse them first - which is the point: the
     * database is the layer that cannot be bypassed, and a future path around the Java code
     * still meets these.</p>
     */
    @Test
    void theMigrationRefusesEveryRowItDeclaresIllegal() {
        Long author = account("rules.author", Role.REQUESTER);
        Long reporter = account("rules.reporter", Role.REQUESTER);
        Long other = account("rules.reporter.two", Role.REQUESTER);
        Long question = openQuestion(author, "Constraint sample");
        Long answer = answer(question, author, "An answer the constraint sample reports on.");

        // Exactly one target: both set, and neither set, are the same failure.
        assertThatThrownBy(() -> insertReport(reporter, question, answer, "SPAM", "OPEN", null, null))
                .as("a report of two things at once")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_community_reports_target");
        assertThatThrownBy(() -> insertReport(reporter, null, null, "SPAM", "OPEN", null, null))
                .as("a report of nothing at all")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_community_reports_target");

        // One report per reporter per target, on each side of the partial index. The two
        // indexes are separate, so the same account reporting a question and an answer is
        // not a duplicate of anything.
        insertReport(reporter, question, null, "SPAM", "OPEN", null, null);
        assertThatThrownBy(() -> insertReport(reporter, question, null, "ABUSIVE", "OPEN", null, null))
                .as("the same account reporting the same question twice")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_community_reports_question");

        insertReport(reporter, null, answer, "OFF_TOPIC", "OPEN", null, null);
        assertThatThrownBy(() -> insertReport(reporter, null, answer, "OTHER", "OPEN", null, null))
                .as("the same account reporting the same answer twice")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_community_reports_answer");

        // Both indexes are per reporter, so a second account reporting the same content is
        // not a duplicate - and a question report never collides with an answer report.
        insertReport(other, question, null, "SPAM", "OPEN", null, null);
        insertReport(other, null, answer, "SPAM", "OPEN", null, null);

        // Each remaining rule gets a fresh question, so the row it writes is illegal for
        // exactly one reason: a row that broke the duplicate index instead would prove
        // nothing about the constraint under test.
        Long settledWithoutATime = openQuestion(author, "Constraint sample two");
        Long openButAlreadyHandled = openQuestion(author, "Constraint sample three");
        Long unknownReason = openQuestion(author, "Constraint sample four");
        Long unknownStatus = openQuestion(author, "Constraint sample five");
        Long settledByNobody = openQuestion(author, "Constraint sample seven");
        Long paddedDetail = openQuestion(author, "Constraint sample six");

        // The status and the moment it was settled are tied to each other: a settled report
        // carries a time, and an open one never claims one. Note what this constraint does
        // *not* mention - see the pair of assertions after it.
        assertThatThrownBy(() -> insertReport(
                other, settledWithoutATime, null, "SPAM", "ACTIONED", null, null))
                .as("a settled report with no handling time")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_community_reports_handled");
        assertThatThrownBy(() -> insertReport(
                other, openButAlreadyHandled, null, "SPAM", "OPEN", null, "2026-10-01T00:00:00Z"))
                .as("an open report that already has a handling time")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_community_reports_handled");

        // Where the constraint's own comment overstates it, pinned down rather than left as
        // prose. The comment says "a settled report always names who settled it and when",
        // but chk_community_reports_handled is written as (status = 'OPEN') = (handled_at IS
        // NULL) and never mentions handled_by_user_id, so this row - settled at a known
        // moment, by nobody - is stored rather than refused.
        //
        // Nothing the product can produce looks like this: resolveReportIfOpen is the only
        // writer of both columns and it sets them together, which is why this is a note and
        // not a live defect. It is recorded as a finding in
        // docs/sprint3/UserA_Community_Implementation_Contract_CN.md rather than repaired by
        // editing a migration that has already run against a real database.
        insertReport(other, settledByNobody, null, "SPAM", "ACTIONED", null,
                "2026-10-01T00:00:00Z");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM community_reports WHERE question_id = ?",
                Integer.class, settledByNobody))
                .as("a settled report whose handler column is empty reaches the table")
                .isEqualTo(1);

        // The reason is one of the five the product offers, and the status one of three.
        assertThatThrownBy(() -> insertReport(
                other, unknownReason, null, "NOT_A_REASON", "OPEN", null, null))
                .as("a reason outside the five the product offers")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_community_reports_reason");
        // The handling time is filled in so that this row is illegal for the status alone.
        // 'PENDING' is not 'OPEN', so an empty handled_at would break
        // chk_community_reports_handled as well - and a row that two constraints refuse
        // proves nothing about either, whichever one PostgreSQL happens to name first.
        assertThatThrownBy(() -> insertReport(
                other, unknownStatus, null, "SPAM", "PENDING", other, "2026-10-01T00:00:00Z"))
                .as("a status outside the three the product offers")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_community_reports_status");

        // A value that reaches the table by any route is stored the way the entity would
        // have stored it: trimmed, and never blank.
        assertThatThrownBy(() -> insertReportWithDetail(other, paddedDetail, "  padded  "))
                .as("a note with surrounding whitespace")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_community_reports_detail");
        assertThatThrownBy(() -> insertReportWithDetail(other, paddedDetail, "   "))
                .as("a note that is blank once trimmed")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_community_reports_detail");
    }

    // -------------------------------------------------- the event's contract

    /**
     * A hide announces itself when - and only when - the transaction that made it commits.
     *
     * <p>The event probe checks commit timing. Durable audit assertions read the production
     * audit_entries table, written atomically by the moderation service.</p>
     */
    @Test
    void aHideAnnouncesItselfOnceItsTransactionCommits() {
        Long author = account("event.author", Role.REQUESTER);
        Long moderator = account("event.moderator", Role.ADMINISTRATOR);
        Long question = openQuestion(author, "A hide that commits");

        assertThat(moderation.hideQuestion(question, moderator)).isTrue();

        assertThat(recorder.hidden())
                .as("the event is delivered once the business transaction has committed")
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.contentId()).isEqualTo(question);
                    assertThat(event.contentType()).isEqualTo(CommunityContentType.QUESTION);
                    assertThat(event.questionId()).isEqualTo(question);
                    assertThat(event.authorId()).isEqualTo(author);
                    assertThat(event.actorUserId()).isEqualTo(moderator);
                    assertThat(event.resultingStatus()).isEqualTo(CommunityContentStatus.HIDDEN);
                    assertThat(event.occurredAt()).isNotNull();
                });

        assertThat(auditRows())
                .as("a listener that wanted to record the hide durably could have")
                .isEqualTo(1);
    }

    /**
     * The same hide, inside a transaction that rolls back.
     *
     * <p>The status change disappears and nothing is announced: no event, no audit row, and
     * the question is still public. This is what "published from inside the business
     * transaction" buys, and it is the half of the contract that a mock publisher cannot
     * show at all.</p>
     */
    @Test
    void aHideInATransactionThatRollsBackAnnouncesNothing() {
        Long author = account("rollback.author", Role.REQUESTER);
        Long moderator = account("rollback.moderator", Role.ADMINISTRATOR);
        Long question = openQuestion(author, "A hide that rolls back");

        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.execute(status -> {
            assertThat(moderation.hideQuestion(question, moderator)).isTrue();
            // The hide is visible from inside the transaction that made it...
            assertThat(statusOfQuestionFromThisTransaction(question)).isEqualTo("HIDDEN");
            status.setRollbackOnly();
            return null;
        });

        assertThat(statusOfQuestion(question))
                .as("the rolled-back hide left the question public")
                .isEqualTo("VISIBLE");
        assertThat(recorder.hidden())
                .as("a rolled-back transaction told nobody")
                .isEmpty();
        assertThat(countRows("audit_entries")).isZero();
        assertThat(countRows("notifications")).isZero();
        assertThat(auditRows())
                .as("and wrote nothing durable")
                .isZero();
    }

    @Test
    void migratedTablesStoreRealNotificationAndAtomicModerationAudit() {
        Long author = account("real.author", Role.REQUESTER);
        Long answerer = account("real.answerer", Role.REQUESTER);
        Long moderator = account("real.admin", Role.ADMINISTRATOR);
        Long question = openQuestion(author, "Real delivery and audit");
        var command = new com.smartfix.community.dto.AnswerFormCommand();
        command.setBody("Please check the projector power cable first.");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            answersTo.post(question, command, answerer);
            tx.setRollbackOnly();
        });
        assertThat(countRows("notifications")).isZero();
        Long answer = answersTo.post(question, command, answerer);
        answersTo.accept(question, answer, author);
        assertThat(jdbc.queryForList("SELECT event_type FROM notifications ORDER BY id", String.class))
                .containsExactly("COMMUNITY_ANSWER_CREATED", "COMMUNITY_ANSWER_ACCEPTED");
        assertThat(jdbc.queryForList("SELECT recipient_id FROM notifications ORDER BY id", Long.class))
                .containsExactly(author, answerer);
        moderation.hideAnswer(answer, moderator);
        moderation.restoreAnswer(answer, moderator);
        assertThat(jdbc.queryForList("SELECT action FROM audit_entries ORDER BY id", String.class))
                .containsExactly("CONTENT_HIDDEN", "CONTENT_RESTORED");
        assertThat(jdbc.queryForList("SELECT actor_user_id FROM audit_entries ORDER BY id", Long.class))
                .containsOnly(moderator);
        assertThat(jdbc.queryForList("SELECT target_id FROM audit_entries ORDER BY id", Long.class))
                .containsOnly(answer);
    }

    // ------------------------------------------------- none of it writes a request

    /**
     * The brief's boundary, checked end to end: reporting, deciding and moderating must not
     * create a row in any of the request-side tables.
     *
     * <p>Every one of those tables is counted before and after a full moderation cycle. The
     * assignment and SLA tables the brief names do not exist in this schema yet - those
     * entities are not built - so there is nothing there to assert on, and this comment is
     * the record of that rather than a claim about them.</p>
     */
    @Test
    void moderationNeverWritesOutsideTheCommunityTables() {
        Long author = account("boundary.author", Role.REQUESTER);
        Long reporter = account("boundary.reporter", Role.REQUESTER);
        Long moderator = account("boundary.moderator", Role.ADMINISTRATOR);
        Long question = openQuestion(author, "A full moderation cycle");
        Long answer = answer(question, author, "An answer inside the moderation cycle.");

        String[] requestTables = {
            "maintenance_requests", "work_orders", "repair_records", "request_status_history",
            "request_attachments", "request_feedback", "request_ticket_sequences"
        };
        List<Integer> before = new ArrayList<>();
        for (String table : requestTables) {
            before.add(countRows(table));
        }

        moderation.reportQuestion(question, report("Something is wrong here."), reporter);
        Long reportId = jdbc.queryForObject(
                "SELECT id FROM community_reports WHERE reporter_id = ? AND question_id = ?",
                Long.class, reporter, question);
        moderation.resolveReport(reportId,
                resolve(CommunityReportStatus.ACTIONED, "Handled.", true), moderator);
        moderation.hideAnswer(answer, moderator);
        moderation.restoreAnswer(answer, moderator);
        moderation.restoreQuestion(question, moderator);

        for (int index = 0; index < requestTables.length; index++) {
            assertThat(countRows(requestTables[index]))
                    .as("moderation must not create %s rows", requestTables[index])
                    .isEqualTo(before.get(index));
        }
    }

    // ------------------------------------------------------------------ seeding

    /**
     * A visible question by {@code authorId}, written directly and committed, so the racing
     * threads - which run on connections of their own - can see it.
     */
    private Long openQuestion(Long authorId, String title) {
        CommunityQuestion question = CommunityQuestion.ask(
                authorId, title, "A question body long enough to pass the length rule.",
                CommunityCategory.OTHER, BASE);
        return questions.saveAndFlush(question).getId();
    }

    private Long answer(Long questionId, Long authorId, String body) {
        return answers.saveAndFlush(
                CommunityAnswer.post(questionId, authorId, body, BASE.plusSeconds(60))).getId();
    }

    private Long openReport(Long reporterId, Long questionId) {
        return reports.saveAndFlush(CommunityReport.ofQuestion(
                reporterId, questionId, CommunityReportReason.SPAM, null, BASE.plusSeconds(120)))
                .getId();
    }

    private Long account(String username, Role role) {
        CreateUserCommand command = new CreateUserCommand();
        command.setUsername(username);
        command.setDisplayName(username + " (test)");
        command.setPassword(PASSWORD);
        command.setRole(role);
        return users.createUser(command, null);
    }

    // ------------------------------------------------------------------- racing

    /** One report of the same question, attempted on its own thread and in its own transaction. */
    private Callable<Long> reportingQuestion(Long questionId, Long reporterId, String detail) {
        return () -> moderation.reportQuestion(questionId, report(detail), reporterId);
    }

    /** One moderator's decision, attempted the same way. */
    private Callable<Boolean> deciding(
            Long reportId, Long actorId, CommunityReportStatus decision, String note, boolean hide) {
        return () -> moderation.resolveReport(reportId, resolve(decision, note, hide), actorId);
    }

    /** One acceptance, attempted the same way. */
    private Callable<Boolean> accepting(Long questionId, Long answerId, Long actorId) {
        return () -> {
            answersTo.accept(questionId, answerId, actorId);
            return true;
        };
    }

    /** One hide, attempted the same way. */
    private Callable<Boolean> hiding(Long answerId, Long actorId) {
        return () -> moderation.hideAnswer(answerId, actorId);
    }

    /**
     * Runs every task at once and reports what each one did.
     *
     * <p>The barrier is what makes this a race rather than a queue: no task is allowed to
     * call the service until every task is ready, so the calls reach the database together
     * and PostgreSQL decides the order. Results come back in the order the tasks were
     * given, so the caller can tell which outcome belongs to which operation.</p>
     */
    private <T> List<Outcome<T>> together(List<? extends Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CyclicBarrier start = new CyclicBarrier(tasks.size());
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    outcomes.add(new Outcome<>(future.get(60, TimeUnit.SECONDS), null));
                } catch (ExecutionException refused) {
                    outcomes.add(new Outcome<>(null, refused.getCause()));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    /** What one racing call did: the value it produced, or the refusal it was stopped by. */
    private record Outcome<T>(T value, Throwable failure) {
        boolean succeeded() {
            return failure == null;
        }
    }

    // --------------------------------------------------- writing illegal rows

    private static ReportContentCommand report(String detail) {
        ReportContentCommand command = new ReportContentCommand();
        command.setReason(CommunityReportReason.SPAM);
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
     * Writes a report row straight into the table.
     *
     * <p>Deliberately below the entity: the point is to reach the constraints, and every
     * factory and service check that would have refused these rows sits above this line.</p>
     */
    private void insertReport(
            Long reporterId, Long questionId, Long answerId,
            String reason, String status, Long handledBy, String handledAt) {
        jdbc.update("""
                INSERT INTO community_reports
                    (question_id, answer_id, reporter_id, reason, status,
                     handled_by_user_id, handled_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS TIMESTAMPTZ), CURRENT_TIMESTAMP)
                """, questionId, answerId, reporterId, reason, status, handledBy, handledAt);
    }

    private void insertReportWithDetail(Long reporterId, Long questionId, String detail) {
        jdbc.update("""
                INSERT INTO community_reports
                    (question_id, reporter_id, reason, detail, status, created_at)
                VALUES (?, ?, ?, ?, 'OPEN', CURRENT_TIMESTAMP)
                """, questionId, reporterId, "SPAM", detail);
    }

    // ------------------------------------------------------- reading the result

    private String statusOfQuestion(Long questionId) {
        return jdbc.queryForObject(
                "SELECT status FROM community_questions WHERE id = ?", String.class, questionId);
    }

    /**
     * The question's status as this transaction sees it.
     *
     * <p>Read through JPA rather than JDBC so the uncommitted change this transaction just
     * made is the value that comes back.</p>
     */
    private String statusOfQuestionFromThisTransaction(Long questionId) {
        return questions.findById(questionId).orElseThrow().getStatus().name();
    }

    private String statusOfAnswer(Long answerId) {
        return jdbc.queryForObject(
                "SELECT status FROM community_answers WHERE id = ?", String.class, answerId);
    }

    private String statusOfReport(Long reportId) {
        return jdbc.queryForObject(
                "SELECT status FROM community_reports WHERE id = ?", String.class, reportId);
    }

    private String noteOfReport(Long reportId) {
        return jdbc.queryForObject(
                "SELECT resolution_note FROM community_reports WHERE id = ?", String.class, reportId);
    }

    private Long handlerOfReport(Long reportId) {
        return jdbc.queryForObject(
                "SELECT handled_by_user_id FROM community_reports WHERE id = ?",
                Long.class, reportId);
    }

    private Timestamp handledAtOfReport(Long reportId) {
        return jdbc.queryForObject(
                "SELECT handled_at FROM community_reports WHERE id = ?",
                Timestamp.class, reportId);
    }

    private Long acceptedAnswerIdOf(Long questionId) {
        List<Long> rows = jdbc.queryForList(
                "SELECT accepted_answer_id FROM community_questions WHERE id = ?",
                Long.class, questionId);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    /** Every accepted answer anywhere that is not publicly visible. The answer is zero. */
    private int questionsPointingAtContentNobodyCanRead() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM community_questions q
                JOIN community_answers a ON a.id = q.accepted_answer_id
                WHERE a.status <> 'VISIBLE'
                """, Integer.class);
        return count == null ? 0 : count;
    }

    private int auditRows() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM audit_entries", Integer.class);
        return count == null ? 0 : count;
    }

    private int countRows(String table) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------------ teardown

    @AfterAll
    static void removeSchema() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                        required("TEST_DB_URL"), required("TEST_DB_USERNAME"),
                        required("TEST_DB_PASSWORD"));
                Statement sql = connection.createStatement()) {
            sql.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        assertThat(value).as(name).isNotBlank();
        return value;
    }

    /**
     * Records what the module announced, at the moment the transaction that announced it
     * committed - the same phase the real notification listener consumes.
     *
     * <p>Test-only, and a listener rather than a mock: the events are published by the real
     * service, in the real transaction. Persistence assertions use the production audit
     * and notification tables, not this probe.</p>
     */
    @TestConfiguration
    static class RecordedEvents {

        @Bean
        HiddenRecorder hiddenRecorder() {
            return new HiddenRecorder();
        }
    }

    /** An in-memory event probe only; audit assertions read the production audit_entries table. */
    static class HiddenRecorder {

        private final List<CommunityContentHiddenEvent> hidden = new CopyOnWriteArrayList<>();
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onHidden(CommunityContentHiddenEvent event) {
            hidden.add(event);
        }

        List<CommunityContentHiddenEvent> hidden() {
            return List.copyOf(hidden);
        }

        void clear() {
            hidden.clear();
        }
    }
}
