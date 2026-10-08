package com.smartfix.auth;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.RegistrationCommand;
import com.smartfix.user.service.UserService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What actually happens when two people register the same username at the same moment.
 *
 * <h2>Why a serial test cannot answer this</h2>
 *
 * <p>{@code RegistrationFlowIT} registers a username twice one after the other, and the
 * second attempt is refused by the courtesy check that runs before the insert - a
 * {@code SELECT} that the first attempt has already committed. Under a real race there is
 * nothing to find: both callers look, both see an empty table, and both insert. The only
 * thing standing between that and two accounts with one username is the unique constraint,
 * and a test that never makes the two calls overlap says nothing about it. That is the
 * whole reason this class exists.</p>
 *
 * <h2>Why PostgreSQL and not H2</h2>
 *
 * <p>The constraint is defined by {@code V2__create_users.sql} and exercised here on the
 * schema Flyway actually builds. H2 would run the same DDL through a different parser and
 * a different lock manager, and a green H2 test would say nothing about the deployed
 * behaviour of the index that is the point of the test. The native-PostgreSQL cases in this
 * project are opt-in for that reason; see the {@code postgres-it} profile.</p>
 *
 * <h2>The two things proved here</h2>
 *
 * <ol>
 *   <li><strong>The race produces one account, and the losers get a sentence.</strong> Four
 *       simultaneous registrations of one username leave exactly one row and three
 *       {@link BusinessConflictException}s - never a raw {@code DataIntegrityViolationException}
 *       and never a second account.</li>
 *   <li><strong>The database is what refuses, not the check.</strong> A duplicate inserted
 *       straight through JDBC, with no service in the way, is rejected by the constraint
 *       itself. That is the backstop the first test leans on, and asserting it separately
 *       keeps this class honest on a machine where the four threads happen not to overlap.</li>
 * </ol>
 *
 * <p>Listed in the {@code maven-failsafe-plugin} excludes and run only under {@code
 * postgres-it} with {@code TEST_DB_URL}, {@code TEST_DB_USERNAME} and {@code
 * TEST_DB_PASSWORD} set. Everything it writes lives in a randomly named schema that is
 * dropped afterwards.</p>
 */
@SpringBootTest(
        properties = {
            "spring.datasource.driver-class-name=org.postgresql.Driver",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            // Four racers each hold a connection for the length of their transaction, so the
            // pool has to be able to hand out four at once; a smaller pool would queue the
            // race inside Hikari and quietly turn it back into a serial test.
            "spring.datasource.hikari.maximum-pool-size=8",
            "smartfix.bootstrap-admin.enabled=false"
        })
class RegistrationConcurrencyPostgresIT {

    /** Synthetic test-only credential, never an application default. */
    private static final String PASSWORD = "TestPassword9";

    private static final String USERNAME = "race.requester";

    private static final String SCHEMA =
            "smartfix_registration_race_" + UUID.randomUUID().toString().replace("-", "");

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

    @Autowired private UserService users;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTheSchema() {
        // The schema outlives a single test method, so each one starts from nothing and can
        // use the plain username above.
        jdbc.update("DELETE FROM users");
    }

    // ------------------------------------------------------------------ the race

    /**
     * Four simultaneous registrations of one username.
     *
     * <p>Each runs on its own thread through the real, transactional service proxy, so each
     * gets its own connection and its own transaction; a {@link CyclicBarrier} releases them
     * together so the calls overlap rather than queue up in the test. All four are likely to
     * pass the courtesy check - that is the point of the barrier - so the loser has to be
     * stopped by the constraint and by nothing else.</p>
     *
     * <p>What the losers get matters as much as how many win. They are anonymous visitors
     * filling in a public form: a {@code DataIntegrityViolationException} escaping to the
     * controller would be a 500 with a stack trace, so each refusal has to be the same
     * {@link BusinessConflictException} the controller already turns into a field-level
     * sentence. The service is also used straight afterwards for a different username, which
     * is how this test notices a transaction left broken behind a caught violation.</p>
     */
    @Test
    void simultaneousRegistrationsOfOneUsernameProduceExactlyOneAccount() throws Exception {
        List<Outcome<Long>> outcomes = together(
                List.of(registering(), registering(), registering(), registering()));

        List<Long> winners = outcomes.stream().filter(Outcome::succeeded).map(Outcome::value).toList();
        List<Throwable> refusals =
                outcomes.stream().filter(outcome -> !outcome.succeeded()).map(Outcome::failure).toList();

        assertThat(winners)
                .as("exactly one of the four registrations may create an account")
                .hasSize(1);
        assertThat(refusals)
                .as("every losing registration is a business conflict, not a database error")
                .hasSize(3)
                .allSatisfy(refusal -> assertThat(refusal)
                        .isInstanceOf(BusinessConflictException.class)
                        .hasMessage("An account with this username already exists."));

        assertThat(rowsWithUsername(USERNAME))
                .as("one username, one row - the invariant the constraint exists for")
                .isEqualTo(1);

        // The winner is an ordinary self-registered account, decided by the server.
        var created = users.findAuthenticationByUsername(USERNAME);
        assertThat(created.role()).isEqualTo(Role.REQUESTER);
        assertThat(created.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(created.passwordHash()).startsWith("$2");

        // And the pool is healthy: a refused transaction left nothing behind that stops the
        // next caller from working.
        assertThat(register("after.the.race")).isNotNull();
        assertThat(rowsWithUsername("after.the.race")).isEqualTo(1);
    }

    // ------------------------------------------------------- the backstop itself

    /**
     * A duplicate that never goes through the service is still refused.
     *
     * <p>This is the assertion that does not depend on thread timing, and it is why the test
     * above can claim the constraint is what settles the race. It also checks the constraint
     * by name: a migration that dropped the unique index would fail here rather than only in
     * production, under load, once.</p>
     *
     * <p>{@code 23505} is PostgreSQL's {@code unique_violation}. Spring's translation of it
     * is {@link DuplicateKeyException}, which is the parent of the
     * {@code DataIntegrityViolationException} the service catches - so the service's
     * translation is being exercised against the real thing here, not against a simulation
     * of it.</p>
     */
    @Test
    void theDatabaseItselfRefusesASecondAccountWithTheSameUsername() {
        assertThat(constraintExists())
                .as("V2's unique constraint, on the schema Flyway builds")
                .isTrue();
        register(USERNAME);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO users (username, display_name, password_hash, role, account_status,
                                   security_version, created_at, updated_at)
                VALUES (?, ?, ?, 'REQUESTER', 'ACTIVE', 0, now(), now())
                """, USERNAME, "Written past the service", "$2a$10$notarealhashnotarealhashnotarealhashnotarealhashnotarealhashno"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasRootCauseInstanceOf(SQLException.class)
                .rootCause()
                .satisfies(cause -> assertThat(((SQLException) cause).getSQLState())
                        .as("PostgreSQL's unique_violation")
                        .isEqualTo("23505"));

        assertThat(rowsWithUsername(USERNAME)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    /** One registration, attempted on its own thread and in its own transaction. */
    private Callable<Long> registering() {
        return () -> {
            RegistrationCommand form = new RegistrationCommand();
            form.setUsername(USERNAME);
            form.setDisplayName("Racing Requester");
            form.setPassword(PASSWORD);
            form.setConfirmPassword(PASSWORD);
            return users.registerRequester(form);
        };
    }

    private Long register(String username) {
        RegistrationCommand form = new RegistrationCommand();
        form.setUsername(username);
        form.setDisplayName(username + " (test)");
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        return users.registerRequester(form);
    }

    /**
     * Runs every task at once and reports what each one did.
     *
     * <p>The barrier is what makes this a race rather than a queue: no task reaches the
     * service until all of them are ready, so the calls land together and PostgreSQL decides
     * the order. Results come back in the order the tasks were given.</p>
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

    private int rowsWithUsername(String username) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE username = ?", Integer.class, username);
        return count == null ? 0 : count;
    }

    private boolean constraintExists() {
        Integer found = jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE schemaname = current_schema() AND indexname = 'uk_users_username'
                """, Integer.class);
        return found != null && found > 0;
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
}
