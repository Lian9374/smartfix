package com.smartfix.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Cleanup only within the caller's isolated migrated PostgreSQL test schema. */
public final class PostgresFixtureCleanup {
    private PostgresFixtureCleanup() {}

    public static void clearDeliveryAndSla(JdbcTemplate jdbc) {
        String database = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                connection -> connection.getMetaData().getDatabaseProductName());
        if (!"PostgreSQL".equals(database)) return;
        String schema = jdbc.queryForObject("SELECT current_schema()", String.class);
        String catalog = jdbc.queryForObject("SELECT current_database()", String.class);
        if (catalog == null || !catalog.endsWith("_test") || schema == null || "public".equals(schema)) {
            throw new IllegalStateException("Fixture cleanup requires an isolated schema in a *_test database");
        }
        jdbc.update("DELETE FROM delivery_tasks");
        jdbc.update("DELETE FROM sla_states");
        jdbc.update("DELETE FROM sla_policies");
        jdbc.update("DELETE FROM audit_entries");
    }
}
