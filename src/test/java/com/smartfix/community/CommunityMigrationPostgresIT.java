package com.smartfix.community;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The community constraints, against a real PostgreSQL server.
 *
 * <h2>Why this cannot be an H2 test</h2>
 *
 * <p>Every other test in this module runs on H2, and H2 gets its schema from the JPA
 * entities rather than from these migrations. The entities deliberately carry no
 * association for {@code accepted_answer_id}, so on H2 there is no constraint to violate
 * and <strong>no H2 test can exercise the rule this file exists to check</strong> - the
 * composite foreign key that makes "the accepted answer is an answer to this question"
 * true. The same applies to the {@code CHECK} constraints, which live only in the
 * migration. Asserting them on H2 would be asserting that Hibernate generates constraints
 * it does not generate.</p>
 *
 * <p>Opt-in, like the other native-PostgreSQL cases: it is listed in the {@code
 * maven-failsafe-plugin} excludes and runs only under the {@code postgres-it} profile with
 * {@code TEST_DB_URL}, {@code TEST_DB_USERNAME} and {@code TEST_DB_PASSWORD} set. It needs
 * a real server, so it cannot run in an environment that has none - which is reported as
 * not-run rather than counted as passed.</p>
 *
 * <p>Everything it does happens in a randomly named schema that is dropped afterwards, so
 * it never touches a schema anyone depends on.</p>
 */
class CommunityMigrationPostgresIT {

    @Test
    void theCommunityConstraintsHoldOnPostgres() throws Exception {
        String url = required("TEST_DB_URL");
        String user = required("TEST_DB_USERNAME");
        String password = required("TEST_DB_PASSWORD");
        assertThat(url).startsWith("jdbc:postgresql:");

        String schema = "smartfix_community_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement sql = connection.createStatement()) {
            assertThat(connection.getCatalog()).endsWith("_test");
            sql.execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure()
                        .callbacks(new com.smartfix.common.configuration.LegacyMigrationCompatibility())
                        .dataSource(url, user, password)
                        .schemas(schema)
                        .defaultSchema(schema)
                        .locations("classpath:db/migration")
                        .load()
                        .migrate();
                sql.execute("SET search_path TO " + schema);

                sql.executeUpdate("INSERT INTO users(id,username,display_name,password_hash,role)"
                        + " VALUES(1,'community.asker','Asker','synthetic-test-hash','REQUESTER')");
                sql.executeUpdate("INSERT INTO users(id,username,display_name,password_hash,role)"
                        + " VALUES(2,'community.answerer','Answerer','synthetic-test-hash','REQUESTER')");

                long firstQuestion = insertQuestion(sql, 1, "First question for the constraint checks");
                long secondQuestion = insertQuestion(sql, 1, "Second question for the constraint checks");
                long ownAnswer = insertAnswer(sql, firstQuestion, 2);
                long foreignAnswer = insertAnswer(sql, secondQuestion, 2);

                // 1. Many open questions coexist. This is what MATCH SIMPLE and the fact
                //    that NULLs are distinct in a UNIQUE constraint together buy: without
                //    either, a board could hold at most one unanswered question.
                for (int index = 0; index < 25; index++) {
                    insertQuestion(sql, 1, "Open question number " + index + " with no answer");
                }
                assertThat(countOf(sql, "SELECT count(*) FROM community_questions"
                        + " WHERE accepted_answer_id IS NULL")).isEqualTo(27);

                // 2. Accepting an answer to a different question is refused. This is the
                //    rule a single-column foreign key could not express, and the whole
                //    reason accepted_answer_id carries a composite key.
                assertThatThrownBy(() -> sql.executeUpdate(
                        "UPDATE community_questions SET accepted_answer_id = " + foreignAnswer
                                + " WHERE id = " + firstQuestion))
                        .isInstanceOf(SQLException.class)
                        .hasFieldOrPropertyWithValue("SQLState", "23503");

                // 3. Accepting this question's own answer is allowed.
                sql.executeUpdate("UPDATE community_questions SET accepted_answer_id = " + ownAnswer
                        + " WHERE id = " + firstQuestion);
                assertThat(acceptedAnswerOf(sql, firstQuestion)).isEqualTo(ownAnswer);

                // 4. An accepted answer cannot be deleted out from under the question that
                //    accepted it - the composite key references it.
                assertThatThrownBy(() -> sql.executeUpdate(
                        "DELETE FROM community_answers WHERE id = " + ownAnswer))
                        .isInstanceOf(SQLException.class)
                        .hasFieldOrPropertyWithValue("SQLState", "23503");

                // 5. Nothing cascades. Deleting an author who has content is refused
                //    rather than silently destroying their questions, which is what the
                //    brief's "never physically delete user content" requires.
                assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM users WHERE id = 2"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM users WHERE id = 1"))
                        .isInstanceOf(SQLException.class);
                assertThat(countOf(sql, "SELECT count(*) FROM community_questions")).isEqualTo(27);
                assertThat(countOf(sql, "SELECT count(*) FROM community_answers")).isEqualTo(2);

                // 6. The title and body bounds are enforced by the table and not only by
                //    the entity, so a value reaching it by another route is still refused.
                assertThatThrownBy(() -> insertQuestion(sql, 1, "ab"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> insertQuestion(sql, 1, "t".repeat(151)))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> insertQuestion(sql, 1, "  Padded on the left"))
                        .isInstanceOf(SQLException.class);

                // 7. Category and status are closed sets in the table too.
                assertThatThrownBy(() -> sql.executeUpdate(
                        "INSERT INTO community_questions(author_id,title,body,category)"
                                + " VALUES(1,'Valid title here','A body long enough to pass.','PRINTER')"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> sql.executeUpdate(
                        "INSERT INTO community_questions(author_id,title,body,category,status)"
                                + " VALUES(1,'Valid title here','A body long enough to pass.','OTHER','DELETED')"))
                        .isInstanceOf(SQLException.class);

                // 8. Withdrawing is a status, and it leaves every row where it was.
                sql.executeUpdate("UPDATE community_questions SET status = 'WITHDRAWN' WHERE id = "
                        + secondQuestion);
                assertThat(countOf(sql, "SELECT count(*) FROM community_questions")).isEqualTo(27);
            } finally {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private long insertQuestion(Statement sql, long authorId, String title) throws SQLException {
        return insertReturningId(sql, "INSERT INTO community_questions(author_id,title,body,category)"
                + " VALUES(" + authorId + "," + literal(title)
                + ",'A body long enough to pass the minimum length rule.','OTHER')");
    }

    private long insertAnswer(Statement sql, long questionId, long authorId) throws SQLException {
        return insertReturningId(sql, "INSERT INTO community_answers(question_id,author_id,body)"
                + " VALUES(" + questionId + "," + authorId
                + ",'An answer long enough to pass the minimum length rule.')");
    }

    private long insertReturningId(Statement sql, String insert) throws SQLException {
        try (ResultSet keys = sql.executeQuery(insert + " RETURNING id")) {
            assertThat(keys.next()).isTrue();
            return keys.getLong(1);
        }
    }

    private long acceptedAnswerOf(Statement sql, long questionId) throws SQLException {
        try (ResultSet row = sql.executeQuery(
                "SELECT accepted_answer_id FROM community_questions WHERE id = " + questionId)) {
            assertThat(row.next()).isTrue();
            return row.getLong(1);
        }
    }

    private long countOf(Statement sql, String query) throws SQLException {
        try (ResultSet row = sql.executeQuery(query)) {
            assertThat(row.next()).isTrue();
            return row.getLong(1);
        }
    }

    /** Single quotes doubled, so a title containing one cannot break the statement. */
    private String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private String required(String key) {
        String value = System.getenv(key);
        assertThat(value).as(key).isNotBlank();
        return value;
    }
}
