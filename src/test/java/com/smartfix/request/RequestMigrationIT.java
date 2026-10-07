package com.smartfix.request;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.UUID;

/** Dedicated PostgreSQL only; upgrades a nonempty Sprint 2 schema without dropping data. */
class RequestMigrationIT {
    @Test
    void upgradesSprint2RowsAndEnforcesLifecycleConstraints() throws Exception {
        String url = required("TEST_DB_URL"),
                user = required("TEST_DB_USERNAME"),
                password = required("TEST_DB_PASSWORD");
        assertThat(url).startsWith("jdbc:postgresql:");
        String schema = "smartfix_c_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(url, user, password);
                Statement s = c.createStatement()) {
            assertThat(c.getCatalog()).endsWith("_test");
            s.execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure()
                        .dataSource(url, user, password)
                        .schemas(schema)
                        .defaultSchema(schema)
                        .locations("classpath:db/migration")
                        .target("5")
                        .load()
                        .migrate();
                s.execute("SET search_path TO " + schema);
                s.executeUpdate(
                        "INSERT INTO users(id,username,display_name,password_hash,role)"
                            + " VALUES(1,'test.requester','Requester','synthetic-test-hash','REQUESTER')");
                s.executeUpdate(
                        "INSERT INTO locations(id,location_code,display_name)"
                                + " VALUES(1,'TEST-01','Test location')");
                s.executeUpdate(
                        "INSERT INTO"
                            + " maintenance_requests(id,ticket_number,requester_id,location_id,title,description,category,urgency_level)"
                            + " VALUES(1,'SF-2026-000001',1,1,'Broken"
                            + " light','Fault','ELECTRICAL','MEDIUM')");
                Flyway latest =
                        Flyway.configure()
                                .dataSource(url, user, password)
                                .schemas(schema)
                                .defaultSchema(schema)
                                .locations("classpath:db/migration")
                                .load();
                // V6-V9 are C's required upgrade; subsequent team migrations must also be allowed.
                assertThat(latest.migrate().migrationsExecuted).isGreaterThanOrEqualTo(4);
                assertThat(java.util.Arrays.stream(latest.info().applied())
                        .filter(migration -> migration.getVersion() != null)
                        .map(migration -> migration.getVersion().getVersion()))
                        .contains("6", "7", "8", "9");
                latest.validate();
                assertThat(latest.migrate().migrationsExecuted).isZero();
                try (ResultSet result =
                        s.executeQuery(
                                "SELECT status,version,title,final_urgency_level FROM"
                                        + " maintenance_requests WHERE id=1")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("SUBMITTED");
                    assertThat(result.getLong(2)).isZero();
                    assertThat(result.getString(3)).isEqualTo("Broken light");
                    assertThat(result.getString(4)).isNull();
                }
                s.executeUpdate("UPDATE maintenance_requests SET status='UNDER_REVIEW' WHERE id=1");
                assertThatThrownBy(
                                () ->
                                        s.executeUpdate(
                                                "UPDATE maintenance_requests SET status='INVALID'"
                                                        + " WHERE id=1"))
                        .isInstanceOf(SQLException.class);
                s.executeUpdate(
                        "INSERT INTO"
                                + " request_status_history(request_id,to_status,changed_by_user_id)"
                                + " VALUES(1,'SUBMITTED',1)");
                assertThatThrownBy(
                                () ->
                                        s.executeUpdate(
                                                "INSERT INTO"
                                                    + " request_status_history(request_id,to_status,changed_by_user_id)"
                                                    + " VALUES(1,'SUBMITTED',1)"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(
                                () ->
                                        s.executeUpdate(
                                                "INSERT INTO"
                                                    + " request_feedback(request_id,requester_id,rating)"
                                                    + " VALUES(1,1,6)"))
                        .isInstanceOf(SQLException.class);
                s.executeUpdate(
                        "INSERT INTO request_feedback(request_id,requester_id,rating)"
                                + " VALUES(1,1,5)");
                assertThatThrownBy(
                                () ->
                                        s.executeUpdate(
                                                "INSERT INTO"
                                                    + " request_feedback(request_id,requester_id,rating)"
                                                    + " VALUES(1,1,4)"))
                        .isInstanceOf(SQLException.class);
            } finally {
                s.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private String required(String key) {
        String value = System.getenv(key);
        assertThat(value).as(key).isNotBlank();
        return value;
    }
}
