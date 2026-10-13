package com.smartfix.common.configuration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes immutable historical DDL even when another published branch created its tables.
 * Both callbacks and the original migration execute in one PostgreSQL transaction.
 * Existing rows/identities are moved back only after the original DDL succeeds.
 */
@Component
public class LegacyMigrationCompatibility implements Callback {
    private static final Map<String, List<String>> TABLES = Map.of(
            "10", List.of("technician_profiles", "technician_skills", "technician_service_areas"),
            "11", List.of("assignments"), "14", List.of("notifications"),
            "20", List.of("notifications"));
    private final List<ArchivedTable> archived = new ArrayList<>();
    private String archiveSchema;
    private String schema;

    @Override
    public boolean supports(Event event, Context context) {
        return (event == Event.BEFORE_EACH_MIGRATE || event == Event.AFTER_EACH_MIGRATE)
                && context.getMigrationInfo() != null
                && context.getMigrationInfo().getVersion() != null
                && TABLES.containsKey(context.getMigrationInfo().getVersion().toString());
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) { return true; }

    @Override
    public String getCallbackName() { return "preserve-published-branch-tables"; }

    @Override
    public void handle(Event event, Context context) {
        try {
            Connection connection = context.getConnection();
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) return;
            if (connection.getAutoCommit()) throw new FlywayException("Compatibility migration requires a transaction.");
            if (event == Event.BEFORE_EACH_MIGRATE) archive(connection, context);
            else restore(connection);
        } catch (SQLException ex) {
            throw new FlywayException("Published-branch schema reconciliation failed; migration must roll back.", ex);
        }
    }

    private void archive(Connection connection, Context context) throws SQLException {
        archived.clear();
        String version = context.getMigrationInfo().getVersion().toString();
        schema = queryString(connection, "SELECT current_schema()");
        List<String> tables = TABLES.get(version);
        long existing = 0;
        for (String table : tables) if (exists(connection, schema, table)) existing++;
        if (existing == 0) return;
        if (existing != tables.size()) throw new FlywayException("Partial legacy schema: inspect it before upgrading V" + version);
        String names = tables.stream().map(t -> "'" + t + "'").reduce((a, b) -> a + "," + b).orElseThrow();
        // Recreating a referenced table must never silently redirect unrelated foreign keys.
        String dependencies = "SELECT count(*) FROM pg_constraint c JOIN pg_class p ON p.oid=c.confrelid "
                + "JOIN pg_namespace n ON n.oid=p.relnamespace JOIN pg_class dependent ON dependent.oid=c.conrelid "
                + "JOIN pg_namespace dn ON dn.oid=dependent.relnamespace WHERE c.contype='f' "
                + "AND n.nspname=" + literal(schema) + " AND p.relname IN (" + names + ") "
                + "AND NOT (dn.nspname=" + literal(schema) + " AND dependent.relname IN (" + names + "))";
        if (queryLong(connection, dependencies) != 0)
            throw new FlywayException("Unexpected foreign keys reference legacy tables; refusing automatic reconciliation.");
        archiveSchema = schema + "_legacy_v" + version;
        execute(connection, "CREATE SCHEMA " + quote(archiveSchema));
        for (String table : tables) {
            String source = qualified(schema, table);
            execute(connection, "LOCK TABLE " + source + " IN ACCESS EXCLUSIVE MODE");
            List<String> columns = new ArrayList<>();
            try (var statement = connection.prepareStatement("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema=? AND table_name=? ORDER BY ordinal_position")) {
                statement.setString(1, schema); statement.setString(2, table);
                try (var rows = statement.executeQuery()) { while (rows.next()) columns.add(rows.getString(1)); }
            }
            String sequence = columns.contains("id")
                    ? queryString(connection, "SELECT pg_get_serial_sequence(" + literal(source) + ",'id')") : null;
            long lastValue = 0; boolean called = false;
            if (sequence != null) {
                try (var statement = connection.createStatement(); var row = statement.executeQuery("SELECT last_value,is_called FROM " + sequence)) {
                    row.next(); lastValue = row.getLong(1); called = row.getBoolean(2);
                }
            }
            archived.add(new ArchivedTable(table, columns, queryLong(connection, "SELECT count(*) FROM " + source), lastValue, called));
            execute(connection, "ALTER TABLE " + source + " SET SCHEMA " + quote(archiveSchema));
        }
    }

    private void restore(Connection connection) throws SQLException {
        if (archived.isEmpty()) return;
        for (ArchivedTable table : archived) {
            String target = qualified(schema, table.name());
            String source = qualified(archiveSchema, table.name());
            if (table.name().equals("notifications")) {
                // V14 adds deduplication; V20 is an older branch's narrower schema.
                // Preserve V14's contract when V20 is executed on a fresh database, too.
                execute(connection, "ALTER TABLE " + target + " ADD COLUMN IF NOT EXISTS dedup_key VARCHAR(200)");
                execute(connection, "ALTER TABLE " + target + " ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");
            }
            String columns = table.columns().stream().map(LegacyMigrationCompatibility::quote)
                    .reduce((a, b) -> a + "," + b).orElseThrow();
            String extraColumn = table.name().equals("notifications") && !table.columns().contains("dedup_key") ? ",dedup_key" : "";
            String extraValue = extraColumn.isEmpty() ? "" : ",'LEGACY_NOTIFICATION:' || id";
            execute(connection, "INSERT INTO " + target + " (" + columns + extraColumn + ") SELECT " + columns + extraValue + " FROM " + source);
            if (queryLong(connection, "SELECT count(*) FROM " + target) != table.rows()
                    || queryLong(connection, "SELECT count(*) FROM ((SELECT " + columns + " FROM " + source
                    + " EXCEPT ALL SELECT " + columns + " FROM " + target + ") UNION ALL (SELECT " + columns + " FROM " + target
                    + " EXCEPT ALL SELECT " + columns + " FROM " + source + ")) differences") != 0)
                throw new FlywayException("Legacy row verification failed for " + table.name());
            String sequence = table.columns().contains("id")
                    ? queryString(connection, "SELECT pg_get_serial_sequence(" + literal(target) + ",'id')") : null;
            if (sequence != null) {
                long max = queryLong(connection, "SELECT COALESCE(max(id),0) FROM " + target);
                long nextBase = Math.max(Math.max(max, table.sequenceValue()), 1);
                execute(connection, "SELECT setval(" + literal(sequence) + "," + nextBase + "," + (table.sequenceCalled() || max > 0) + ")");
            }
            if (table.name().equals("notifications")) {
                execute(connection, "ALTER TABLE " + target + " ALTER COLUMN dedup_key SET NOT NULL");
                execute(connection, "CREATE UNIQUE INDEX IF NOT EXISTS uk_notifications_dedup_key ON " + target + " (dedup_key)");
            }
        }
        // Drop only our verified archive, without CASCADE. Unknown dependencies cause rollback.
        String tables = archived.stream().map(t -> qualified(archiveSchema, t.name()))
                .reduce((a, b) -> a + "," + b).orElseThrow();
        execute(connection, "DROP TABLE " + tables);
        execute(connection, "DROP SCHEMA " + quote(archiveSchema));
        archived.clear();
    }

    private static boolean exists(Connection connection, String schema, String table) throws SQLException {
        return queryString(connection, "SELECT to_regclass(" + literal(qualified(schema, table)) + ")") != null;
    }
    private static String quote(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
    private static String literal(String value) { return "'" + value.replace("'", "''") + "'"; }
    private static String qualified(String schema, String table) { return quote(schema) + "." + quote(table); }
    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static String queryString(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var row = statement.executeQuery(sql)) { row.next(); return row.getString(1); }
    }
    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var row = statement.executeQuery(sql)) { row.next(); return row.getLong(1); }
    }
    private record ArchivedTable(String name, List<String> columns, long rows, long sequenceValue, boolean sequenceCalled) {}
}
