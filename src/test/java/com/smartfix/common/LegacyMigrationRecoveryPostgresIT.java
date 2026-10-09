package com.smartfix.common;

import com.smartfix.common.configuration.LegacyMigrationCompatibility;
import com.smartfix.common.configuration.LegacyMigrationRecoveryConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL, isolated schemas only; historical SQL is copied unchanged for legacy fixtures. */
class LegacyMigrationRecoveryPostgresIT {
    @TempDir Path legacy;
    String schema;
    Connection connection;

    @BeforeEach void setup() throws Exception {
        connection = DriverManager.getConnection(env("TEST_DB_URL"), env("TEST_DB_USERNAME"), env("TEST_DB_PASSWORD"));
        assertThat(connection.getCatalog()).endsWith("_test");
        schema = "legacy_recovery_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        try (var paths = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path path : paths.toList()) {
                String version = path.getFileName().toString().split("__")[0];
                if (!Set.of("V10", "V11", "V14", "V16", "V25").contains(version))
                    Files.copy(path, legacy.resolve(path.getFileName()));
            }
        }
    }
    @AfterEach void cleanup() throws Exception {
        if (connection != null) {
            try { execute("DROP SCHEMA " + schema + " CASCADE"); }
            finally { connection.close(); }
        }
    }
    Flyway configured(String location, boolean callbacks) {
        var config = Flyway.configure().dataSource(env("TEST_DB_URL"), env("TEST_DB_USERNAME"), env("TEST_DB_PASSWORD"))
                .schemas(schema).defaultSchema(schema).locations(location);
        if (callbacks) config.callbacks(new LegacyMigrationCompatibility());
        return config.load();
    }
    void legacyDatabase() {
        configured("filesystem:" + legacy.toAbsolutePath(), false).migrate();
    }
    void seed() throws Exception {
        execute("INSERT INTO users(id,username,display_name,password_hash,role) VALUES "
                + "(101,'fixture.requester','Requester','synthetic-hash','REQUESTER'),"
                + "(102,'fixture.engineer','Engineer','synthetic-hash','TECHNICIAN'),"
                + "(103,'fixture.admin','Admin','synthetic-hash','ADMINISTRATOR')");
        execute("INSERT INTO locations(id,location_code,display_name) VALUES(9000,'RECOVERY','Recovery fixture')");
        execute("INSERT INTO maintenance_requests(id,ticket_number,requester_id,location_id,title,description,category,urgency_level) "
                + "VALUES(8000,'SF-2026-008000',101,9000,'Fixture issue','Original request','ELECTRICAL','MEDIUM')");
        execute("INSERT INTO technician_profiles(id,user_id,availability_status,version) VALUES(7000,102,'BUSY',5)");
        execute("INSERT INTO technician_skills VALUES(7000,'ELECTRICAL')");
        execute("INSERT INTO technician_service_areas VALUES(7000,9000)");
        execute("INSERT INTO assignments(id,request_id,technician_id,assigned_by_user_id,reason) VALUES(6000,8000,102,103,'Original assignment')");
        execute("INSERT INTO notifications(id,recipient_id,event_type,title,message,reference_id,created_at,read_at) "
                + "VALUES(5000,101,'LEGACY_EVENT','Original title','Original message',8000,'2026-10-01T00:00:00Z','2026-10-02T00:00:00Z')");
    }
    @Test void legacyV24UpgradesWithoutLosingRowsAndStrictValidationWorksAfterwards() throws Exception {
        legacyDatabase(); seed();
        Flyway current = configured("classpath:db/migration", true);
        assertThatThrownBy(current::validate).hasMessageContaining("not applied");
        new LegacyMigrationRecoveryConfiguration().recoverPublishedBranchMigrations().migrate(current);
        current.validate();
        assertThat(current.migrate().migrationsExecuted).isZero();
        assertThat(number("SELECT count(*) FROM flyway_schema_history WHERE version IN ('10','11','14','16','25') AND success")).isEqualTo(5);
        assertThat(number("SELECT count(*) FROM technician_profiles WHERE id=7000 AND user_id=102 AND availability_status='BUSY' AND version=5")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM technician_skills WHERE technician_profile_id=7000 AND category='ELECTRICAL'")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM technician_service_areas WHERE technician_profile_id=7000 AND location_id=9000")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM assignments WHERE id=6000 AND reason='Original assignment' AND active")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM notifications WHERE id=5000 AND message='Original message' AND read_at='2026-10-02T00:00:00Z' AND dedup_key='LEGACY_NOTIFICATION:5000'")).isEqualTo(1);
        execute("INSERT INTO notifications(recipient_id,event_type,title,message,dedup_key) VALUES(101,'NEW_EVENT','New title','New message','new-key')");
        assertThat(number("SELECT id FROM notifications WHERE dedup_key='new-key'")).isGreaterThan(5000);
        assertThat(number("SELECT count(*) FROM information_schema.schemata WHERE schema_name LIKE '" + schema + "_legacy_%'")).isZero();
        assertThat(number("SELECT count(*) FROM announcements")).isZero();
    }
    @Test void freshOrderedChainPreservesDeduplicationAcrossV14AndV20() throws Exception {
        Flyway initial = configured("classpath:db/migration", true);
        Flyway.configure().configuration(initial.getConfiguration()).target("19").load().migrate();
        execute("INSERT INTO users(id,username,display_name,password_hash,role) VALUES(101,'fresh.requester','Requester','synthetic-hash','REQUESTER')");
        execute("INSERT INTO notifications(id,recipient_id,event_type,title,message,dedup_key) VALUES(42,101,'EVENT','Title','Message','keep-this-key')");
        initial.migrate(); initial.validate();
        assertThat(number("SELECT count(*) FROM notifications WHERE id=42 AND dedup_key='keep-this-key'")).isEqualTo(1);
        assertThatThrownBy(() -> execute("INSERT INTO notifications(recipient_id,event_type,title,message,dedup_key) VALUES(101,'EVENT','Title','Message','keep-this-key')"))
                .isInstanceOf(java.sql.SQLException.class);
        assertThat(initial.migrate().migrationsExecuted).isZero();
    }
    @Test void incompatibleLegacyDataRollsBackOriginalTableAndDoesNotRecordMigrationSuccess() throws Exception {
        legacyDatabase(); seed();
        execute("UPDATE notifications SET title='' WHERE id=5000");
        Flyway current = configured("classpath:db/migration", true);
        assertThatThrownBy(() -> new LegacyMigrationRecoveryConfiguration().recoverPublishedBranchMigrations().migrate(current))
                .hasMessageContaining("reconciliation failed");
        assertThat(number("SELECT count(*) FROM notifications WHERE id=5000 AND title='' AND message='Original message'")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM flyway_schema_history WHERE version='14'")).isZero();
        assertThat(number("SELECT count(*) FROM information_schema.columns WHERE table_schema='" + schema + "' AND table_name='notifications' AND column_name='dedup_key'")).isZero();
        assertThat(number("SELECT count(*) FROM information_schema.schemata WHERE schema_name='" + schema + "_legacy_v14'")).isZero();
    }
    @Test void unknownOlderMigrationIsRefusedBeforeAnyRecoveryWrites() throws Exception {
        legacyDatabase(); seed();
        Path unexpected = Files.createDirectory(legacy.resolve("unexpected"));
        Files.writeString(unexpected.resolve("V13__unexpected.sql"), "SELECT 1;");
        Flyway current = Flyway.configure().configuration(configured("classpath:db/migration", true).getConfiguration())
                .locations("classpath:db/migration", "filesystem:" + unexpected.toAbsolutePath()).load();
        long originalHistory = number("SELECT count(*) FROM flyway_schema_history");
        assertThatThrownBy(() -> new LegacyMigrationRecoveryConfiguration().recoverPublishedBranchMigrations().migrate(current))
                .hasMessageContaining("Unexpected older migrations");
        assertThat(number("SELECT count(*) FROM flyway_schema_history")).isEqualTo(originalHistory);
        assertThat(number("SELECT count(*) FROM technician_profiles WHERE id=7000")).isEqualTo(1);
    }
    @Test void unexpectedReferencingTableIsRefusedBeforeArchivingData() throws Exception {
        legacyDatabase(); seed();
        execute("CREATE TABLE extra_dependency(profile_id BIGINT REFERENCES technician_profiles(id))");
        execute("INSERT INTO extra_dependency VALUES(7000)");
        Flyway current = configured("classpath:db/migration", true);
        assertThatThrownBy(() -> new LegacyMigrationRecoveryConfiguration().recoverPublishedBranchMigrations().migrate(current))
                .hasMessageContaining("Unexpected foreign keys");
        assertThat(number("SELECT count(*) FROM technician_profiles WHERE id=7000 AND version=5")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM flyway_schema_history WHERE version='10'")).isZero();
    }
    void execute(String sql) throws Exception { try (var statement = connection.createStatement()) { statement.execute(sql); } }
    long number(String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) { rows.next(); return rows.getLong(1); }
    }
    static String env(String name) { String value = System.getenv(name); assertThat(value).as(name).isNotBlank(); return value; }
}
