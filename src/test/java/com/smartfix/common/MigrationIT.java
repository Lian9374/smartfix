package com.smartfix.common;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Opt-in real PostgreSQL check: mvn -Ppostgres-it clean verify. Never silently skipped. */
class MigrationIT {
    @Test
    void migrationsApplyToAnEmptySchemaAndEnforceAccountConstraints() throws Exception {
        String url = required("TEST_DB_URL");
        String username = required("TEST_DB_USERNAME");
        String password = required("TEST_DB_PASSWORD");
        assertThat(url).as("Use a dedicated PostgreSQL test database").startsWith("jdbc:postgresql:");
        String schema = "smartfix_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertThat(connection.getCatalog()).as("Test database name must end in _test").endsWith("_test");
            // Only this new random schema is removed, never an existing application schema/database.
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
            }
            try {
                Flyway flyway = Flyway.configure().dataSource(url, username, password)
                        .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load();
                assertThat(flyway.migrate().migrationsExecuted).isGreaterThanOrEqualTo(2);
                flyway.validate();
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                try (var statement = connection.createStatement()) {
                    statement.execute("SET search_path TO " + schema);
                    statement.executeUpdate("INSERT INTO users (username, display_name, password_hash, role) "
                            + "VALUES ('alice', 'Alice', 'synthetic-test-hash', 'REQUESTER')");
                    assertThatThrownBy(() -> statement.executeUpdate(
                            "INSERT INTO users (username, display_name, password_hash, role) "
                            + "VALUES ('alice', 'Duplicate', 'synthetic-test-hash', 'REQUESTER')"))
                            .isInstanceOf(SQLException.class)
                            .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23505"));
                    assertThatThrownBy(() -> statement.executeUpdate(
                            "INSERT INTO users (username, display_name, password_hash, role) "
                            + "VALUES ('bad.role', 'Invalid', 'synthetic-test-hash', 'FACILITY_OFFICER')"))
                            .isInstanceOf(SQLException.class)
                            .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
                }
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA " + schema + " CASCADE");
                }
            }
        }
    }

    private String required(String name) {
        String value = System.getenv(name);
        assertThat(value).as(name + " is required for -Ppostgres-it").isNotBlank();
        return value;
    }
}

