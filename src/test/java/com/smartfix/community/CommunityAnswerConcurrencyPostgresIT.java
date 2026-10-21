package com.smartfix.community;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.community.domain.CommunityAnswer;
import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityQuestion;
import com.smartfix.community.event.CommunityAnswerAcceptedEvent;
import com.smartfix.community.repository.CommunityAnswerRepository;
import com.smartfix.community.repository.CommunityQuestionRepository;
import com.smartfix.community.service.CommunityAnswerService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
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

/**
 * The two acceptance races, run against a real PostgreSQL server on genuinely separate
 * connections and separate transactions.
 *
 * <h2>Why the serial tests are not enough</h2>
 *
 * <p>{@code CommunityAnswerServiceTest} verifies the call order with Mockito and asserts
 * that the conditional update was reached; {@code CommunityPagesIT} drives the real
 * controllers over H2. Neither can observe two transactions competing for the same row:
 * a mocked repository has no lock to contend for, and nothing in a single-threaded test
 * ever has to wait. Both of the properties checked here - "at most one accepted answer"
 * and "an accepted answer is never a withdrawn one" - are statements about what happens
 * when one transaction holds a row lock while another wants it, so the only honest way to
 * check them is to make that happen. {@code CommunityAnswerServiceTest} says the same
 * thing in its own header, and points here.</p>
 *
 * <h2>Why PostgreSQL and not H2</h2>
 *
 * <p>H2 is the database every other test in this module runs on, and its {@code SELECT
 * ... FOR UPDATE} and lock manager are not PostgreSQL's. What is under test is the
 * behaviour of the deployed server: whether a blocked {@code FOR UPDATE} re-reads the row
 * once the lock is released, and whether the conditional {@code UPDATE} then sees the
 * committed state. A green H2 test would say nothing about either. The invariant also
 * leans on the composite foreign key from {@code V18}, which exists only in the migration
 * and never on the entity-created H2 schema.</p>
 *
 * <h2>How the race is made</h2>
 *
 * <p>Each racing call runs on its own thread, calls the real, transactional
 * {@link CommunityAnswerService} through its proxy, and therefore gets its own connection
 * and its own transaction from the pool. A {@link CyclicBarrier} releases every thread at
 * the same moment, so the calls overlap instead of queueing up in the test itself. What
 * happens after that is decided by the database, which is the whole point.</p>
 *
 * <h2>Opt-in, and self-contained</h2>
 *
 * <p>Listed in the {@code maven-failsafe-plugin} excludes and run only under the {@code
 * postgres-it} profile with {@code TEST_DB_URL}, {@code TEST_DB_USERNAME} and {@code
 * TEST_DB_PASSWORD} set, like the other native-PostgreSQL cases. It needs a real server,
 * so an environment without one reports it as not run rather than as passed. Everything it
 * writes lives in a randomly named schema that is dropped afterwards.</p>
 */
@SpringBootTest(
        properties = {
            "spring.datasource.driver-class-name=org.postgresql.Driver",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            // Four racers each hold a connection for the length of their transaction, and
            // one of them holds it while the others block. A pool smaller than the number
            // of racers would deadlock the test pool, not the row lock.
            "spring.datasource.hikari.maximum-pool-size=8",
            "smartfix.bootstrap-admin.enabled=false"
        })
@Import(CommunityAnswerConcurrencyPostgresIT.RecordedEvents.class)
class CommunityAnswerConcurrencyPostgresIT {

    /** Synthetic test-only credential, never an application default. */
    private static final String PASSWORD = "TestPassword9";

    private static final Instant BASE = Instant.parse("2026-10-01T08:00:00Z");

    private static final String SCHEMA =
            "smartfix_community_race_" + UUID.randomUUID().toString().replace("-", "");

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

    @Autowired private CommunityAnswerService answersTo;
    @Autowired private com.smartfix.community.service.CommunityQuestionService questionService;
    @Autowired private com.smartfix.community.config.CommunityProperties properties;
    @Autowired private CommunityQuestionRepository questions;
    @Autowired private CommunityAnswerRepository answers;
    @Autowired private UserService users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AcceptanceRecorder recorder;

    @BeforeEach
    void emptyTheSchema() {
        // The schema outlives a single test method, so each one starts from nothing and can
        // create its accounts under plain names.
        recorder.clear();
        properties.getPosting().setMaxPerRateLimitWindow(20);
        properties.getPosting().setDuplicateDetectionEnabled(true);
        jdbc.update("DELETE FROM notifications");
        // Break the composite acceptance reference before deleting its answer rows.
        jdbc.update("UPDATE community_questions SET accepted_answer_id = NULL");
        jdbc.update("DELETE FROM community_answers");
        jdbc.update("DELETE FROM community_questions");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void concurrentQuestionsCannotExceedTheRollingQuota() throws Exception {
        Long author = account("quota.asker", Role.REQUESTER);
        properties.getPosting().setMaxPerRateLimitWindow(2);
        List<Callable<Long>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final int number = i;
            calls.add(() -> {
                var command = new com.smartfix.community.dto.QuestionFormCommand();
                command.setTitle("Concurrent quota question " + number);
                command.setBody("A synthetic question for strict concurrent quota verification.");
                command.setCategory(CommunityCategory.OTHER);
                return questionService.ask(command, author);
            });
        }
        var outcomes = together(calls);
        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(2);
        assertThat(outcomes.stream().filter(o -> !o.succeeded()).map(Outcome::failure))
                .hasSize(6).allSatisfy(e -> assertThat(e).isInstanceOf(com.smartfix.common.exception.InputValidationException.class));
        assertThat(questions.findByAuthorId(author, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
    }

    @Test
    void concurrentAnswersAcrossDifferentQuestionsShareTheAuthorsQuota() throws Exception {
        Long asker = account("quota.asker", Role.REQUESTER);
        Long author = account("quota.answerer", Role.REQUESTER);
        properties.getPosting().setMaxPerRateLimitWindow(2);
        List<Callable<Long>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Long question = openQuestion(asker, "Concurrent answer quota " + i);
            calls.add(() -> {
                var command = new com.smartfix.community.dto.AnswerFormCommand();
                command.setBody("A synthetic answer for strict concurrent quota verification.");
                return answersTo.post(question, command, author);
            });
        }
        var outcomes = together(calls);
        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(2);
        assertThat(outcomes.stream().filter(o -> !o.succeeded()).map(Outcome::failure))
                .hasSize(6).allSatisfy(e -> assertThat(e).isInstanceOf(com.smartfix.common.exception.InputValidationException.class));
        assertThat(answers.findByAuthorId(author, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
    }

    @Test
    void concurrentIdenticalQuestionsAreInsertedOnlyOnce() throws Exception {
        Long author = account("duplicate.asker", Role.REQUESTER);
        var command = new com.smartfix.community.dto.QuestionFormCommand();
        command.setTitle("Identical simultaneous question");
        command.setBody("An identical question submitted concurrently from several sessions.");
        command.setCategory(CommunityCategory.OTHER);
        var outcomes = together(java.util.Collections.nCopies(8, (Callable<Long>) () -> questionService.ask(command, author)));
        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
        assertThat(outcomes.stream().filter(o -> !o.succeeded()).map(Outcome::failure))
                .hasSize(7).allSatisfy(e -> assertThat(e).isInstanceOf(com.smartfix.common.exception.InputValidationException.class));
    }

    // ------------------------------------------------------- two acceptances

    /**
     * Four answerers, four visible answers, four simultaneous acceptances by the question's
     * author. Exactly one may be recorded.
     *
     * <p>This is the invariant plan section 12.2 is built around, and the state it guards
     * is a transition rather than a value: the first caller to reach the conditional
     * {@code UPDATE} moves {@code accepted_answer_id} from null, and every later one finds
     * the column already set and affects no row. The three losers must each see a business
     * conflict rather than an error page, an exception from the lock, or - the failure this
     * test exists to catch - a second success.</p>
     *
     * <p>The published events are checked too, because "the losers told nobody" is not
     * visible in the database. An acceptance that lost must not have announced itself; a
     * winner that announced itself twice would double-notify the same answerer.</p>
     */
    @Test
    void fourSimultaneousAcceptancesLeaveExactlyOneAcceptedAnswer() throws Exception {
        Long asker = account("race.asker", Role.REQUESTER);
        Long question = openQuestion(asker, "Four simultaneous acceptances");
        List<Long> claimants = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            claimants.add(answer(question, account("race.answerer" + index, Role.TECHNICIAN),
                    "One of four answers racing for the same acceptance."));
        }

        List<Outcome<Long>> outcomes = together(claimants.stream()
                .map(candidate -> accepting(question, candidate, asker))
                .toList());

        List<Long> winners = outcomes.stream().filter(Outcome::succeeded).map(Outcome::value).toList();
        List<Throwable> refusals =
                outcomes.stream().filter(outcome -> !outcome.succeeded()).map(Outcome::failure).toList();

        assertThat(winners)
                .as("exactly one of the four acceptances may be recorded")
                .hasSize(1);
        assertThat(refusals)
                .as("every losing acceptance is a business conflict, not a lock failure")
                .hasSize(3)
                .allSatisfy(refusal -> assertThat(refusal).isInstanceOf(BusinessConflictException.class));

        assertThat(acceptedAnswerIdOf(question)).isEqualTo(winners.get(0));
        assertThat(answersThatAreNotVisibleYetAccepted()).isZero();

        assertThat(recorder.accepted())
                .as("the winner announced itself once and the losers announced nothing")
                .singleElement()
                .extracting(CommunityAnswerAcceptedEvent::answerId)
                .isEqualTo(winners.get(0));
    }

    // ------------------------------------------------- acceptance vs withdrawal

    /**
     * The question's author accepts one answer while its author withdraws it.
     *
     * <p>Without the row lock this interleaving is reachable: the acceptance reads the
     * answer as {@code VISIBLE}, the withdrawal commits, and the acceptance is written
     * anyway - leaving a question whose accepted answer nobody can read. The composite
     * foreign key does not catch it, because it proves the answer belongs to the question
     * and not that it is still visible.</p>
     *
     * <p>Both orders are legal and only one of them can happen; which one is the database's
     * decision and is not asserted. What <em>is</em> asserted is that they leave the same
     * state, which is what makes the race safe to lose either way:</p>
     *
     * <ul>
     *   <li>the withdrawal always succeeds - it is the answer's author acting on their own
     *       content, and nothing else in the race can refuse it;</li>
     *   <li>the answer ends up withdrawn, and the question ends up open, whether the
     *       acceptance won and was then cleared in the same breath or was refused because
     *       the answer was already out of view;</li>
     *   <li>no withdrawn answer is left as anybody's accepted answer.</li>
     * </ul>
     */
    @Test
    void aWithdrawalRacingAnAcceptanceNeverLeavesAWithdrawnAnswerAccepted() throws Exception {
        Long asker = account("race.asker", Role.REQUESTER);
        Long answerer = account("race.answerer", Role.TECHNICIAN);
        Long question = openQuestion(asker, "A withdrawal racing an acceptance");
        Long answer = answer(question, answerer, "An answer its author is about to withdraw.");

        List<Outcome<Long>> outcomes = together(List.of(
                accepting(question, answer, asker),
                withdrawing(answer, answerer)));

        Outcome<Long> acceptance = outcomes.get(0);
        Outcome<Long> withdrawal = outcomes.get(1);

        assertThat(withdrawal.succeeded())
                .as("withdrawing your own answer is refused by nothing in this race")
                .isTrue();
        if (!acceptance.succeeded()) {
            // The withdrawal reached the question row first, so by the time the acceptance
            // looked, the answer was no longer VISIBLE. That is the visibility conflict and
            // not the already-accepted one - this question had no acceptance to begin with.
            assertThat(acceptance.failure())
                    .isInstanceOf(BusinessConflictException.class);
        }

        assertThat(statusOfAnswer(answer)).isEqualTo("WITHDRAWN");
        assertThat(acceptedAnswerIdOf(question))
                .as("withdrawing the accepted answer opens the question again")
                .isNull();
        assertThat(answersThatAreNotVisibleYetAccepted())
                .as("the invariant the lock exists for")
                .isZero();
        assertThat(recorder.accepted())
                .as("a refused acceptance announced nothing")
                .hasSize(acceptance.succeeded() ? 1 : 0);
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

    private Long account(String username, Role role) {
        CreateUserCommand command = new CreateUserCommand();
        command.setUsername(username);
        command.setDisplayName(username + " (test)");
        command.setPassword(PASSWORD);
        command.setRole(role);
        return users.createUser(command, null);
    }

    // ------------------------------------------------------------------- racing

    /** One accepted answer, attempted on its own thread and in its own transaction. */
    private Callable<Long> accepting(Long questionId, Long answerId, Long actorId) {
        return () -> {
            answersTo.accept(questionId, answerId, actorId);
            return answerId;
        };
    }

    /** One withdrawal, attempted the same way. */
    private Callable<Long> withdrawing(Long answerId, Long actorId) {
        return () -> {
            answersTo.withdraw(answerId, actorId);
            return answerId;
        };
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

    // ------------------------------------------------------- reading the result

    private Long acceptedAnswerIdOf(Long questionId) {
        List<Long> rows = jdbc.queryForList(
                "SELECT accepted_answer_id FROM community_questions WHERE id = ?",
                Long.class, questionId);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private String statusOfAnswer(Long answerId) {
        return jdbc.queryForObject(
                "SELECT status FROM community_answers WHERE id = ?", String.class, answerId);
    }

    /** Every accepted answer anywhere that is not publicly visible. The answer is zero. */
    private int answersThatAreNotVisibleYetAccepted() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM community_questions q
                JOIN community_answers a ON a.id = q.accepted_answer_id
                WHERE a.status <> 'VISIBLE'
                """, Integer.class);
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
     * committed - the same phase the future notification module will listen on.
     *
     * <p>Test-only, and a listener rather than a mock: the events are published by the real
     * service, in the real transaction, and this is the only way to observe the one thing
     * the database cannot show - that a refused acceptance told nobody.</p>
     */
    @TestConfiguration
    static class RecordedEvents {

        @Bean
        AcceptanceRecorder acceptanceRecorder() {
            return new AcceptanceRecorder();
        }
    }

    /** The recorded acceptances, in commit order. */
    static class AcceptanceRecorder {

        private final List<CommunityAnswerAcceptedEvent> accepted = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onAccepted(CommunityAnswerAcceptedEvent event) {
            accepted.add(event);
        }

        List<CommunityAnswerAcceptedEvent> accepted() {
            return List.copyOf(accepted);
        }

        void clear() {
            accepted.clear();
        }
    }
}
