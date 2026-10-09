package com.smartfix.dispatch;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Tests the PostgreSQL upgrade from V10 and preserves existing profiles and assignment history. */
class AssignmentMigrationIT {
    @Test
    void upgradesNonemptyV10AndEnforcesOneActiveAssignmentWhileRetainingHistory() throws Exception {
        String url = required("TEST_DB_URL"), user = required("TEST_DB_USERNAME"), password = required("TEST_DB_PASSWORD");
        assertThat(url).startsWith("jdbc:postgresql:");
        String schema = "smartfix_b_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement sql = connection.createStatement()) {
            assertThat(connection.getCatalog()).endsWith("_test");
            sql.execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                        .locations("classpath:db/migration").target("10").load().migrate();
                sql.execute("SET search_path TO " + schema);
                sql.executeUpdate("INSERT INTO users(id,username,display_name,password_hash,role) VALUES "
                        + "(1,'migration.requester','Requester','synthetic-test-hash','REQUESTER'),"
                        + "(2,'migration.tech','Technician','synthetic-test-hash','TECHNICIAN'),"
                        + "(3,'migration.admin','Administrator','synthetic-test-hash','ADMINISTRATOR')");
                sql.executeUpdate("INSERT INTO locations(id,location_code,display_name) VALUES(1,'TEST','Test location')");
                sql.executeUpdate("INSERT INTO technician_profiles(user_id,availability_status) VALUES(2,'AVAILABLE')");
                sql.executeUpdate("INSERT INTO maintenance_requests(id,ticket_number,requester_id,location_id,title,description,category,urgency_level) "
                        + "VALUES(1,'SF-2026-000001',1,1,'Test repair','Synthetic request','ELECTRICAL','MEDIUM')");
                Flyway latest = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                        .locations("classpath:db/migration").load();
                assertThat(latest.migrate().migrationsExecuted).isGreaterThanOrEqualTo(1);
                latest.validate();
                try (var result = sql.executeQuery("SELECT completed FROM account_initialization WHERE id=1")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean(1)).as("An existing administrator closes bootstrap during upgrade").isTrue();
                }
                assertThat(latest.migrate().migrationsExecuted).isZero();
                try (var result = sql.executeQuery("SELECT user_id FROM technician_profiles")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getLong(1)).isEqualTo(2);
                }
                String insert = "INSERT INTO assignments(request_id,technician_id,assigned_by_user_id) VALUES(1,2,3)";
                sql.executeUpdate(insert);
                assertThatThrownBy(() -> sql.executeUpdate(insert)).isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23505"));
                assertThatThrownBy(() -> sql.executeUpdate("UPDATE assignments SET active=false"))
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
                for (int i = 0; i < 2; i++) {
                    sql.executeUpdate("UPDATE assignments SET active=false,deactivated_by_user_id=3,"
                            + "deactivated_at=CURRENT_TIMESTAMP,deactivation_reason='Coverage' WHERE active");
                    sql.executeUpdate(insert);
                }
                try (var result = sql.executeQuery("SELECT COUNT(*),COUNT(*) FILTER (WHERE active) FROM assignments")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(3);
                    assertThat(result.getInt(2)).isEqualTo(1);
                }
                assertThatThrownBy(() -> sql.executeUpdate("INSERT INTO assignments(request_id,technician_id,assigned_by_user_id) VALUES(999,2,3)"))
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23503"));
            } finally {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        assertThat(value).as(name + " is required for -Ppostgres-it").isNotBlank();
        return value;
    }
}
