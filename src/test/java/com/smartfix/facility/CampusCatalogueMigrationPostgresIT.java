package com.smartfix.facility;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in migration check in a new schema of a dedicated *_test PostgreSQL database. */
class CampusCatalogueMigrationPostgresIT {
    @Test void catalogueAddsOnlyBuildingChoicesAndPreservesExistingRoomsAndDisabledEntries() throws Exception {
        String url = required("TEST_DB_URL"), username = required("TEST_DB_USERNAME"), password = required("TEST_DB_PASSWORD");
        String schema = "nus_map_it_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, username, password); var sql = connection.createStatement()) {
            assertTrue(connection.getCatalog().endsWith("_test"), "Use a dedicated test database");
            sql.execute("CREATE SCHEMA " + schema);
            try {
                var flyway = Flyway.configure().callbacks(new com.smartfix.common.configuration.LegacyMigrationCompatibility()).dataSource(url, username, password).schemas(schema)
                        .defaultSchema(schema).locations("classpath:db/migration").target("21").load();
                flyway.migrate();
                sql.execute("SET search_path TO " + schema);
                var resource = getClass().getResourceAsStream("/data/nus-campus-buildings.json");
                var buildings = new ObjectMapper().readTree(resource).get("buildings");
                String firstCode = "NUS-" + buildings.get(0).get("id").asText().substring(4);
                // Deliberately explicit id: verifies the migration handles restored identity sequences.
                sql.executeUpdate("INSERT INTO locations(id,location_code,building,floor,room,display_name) "
                        + "VALUES(999,'OLD-COM1','COM1','2','ROOM_SECRET','Existing COM1 room')");
                try (var insert = connection.prepareStatement("INSERT INTO locations(location_code,display_name,active) VALUES(?,'Previously disabled',FALSE)")) {
                    insert.setString(1, firstCode); insert.executeUpdate();
                }
                var latest = Flyway.configure().callbacks(new com.smartfix.common.configuration.LegacyMigrationCompatibility()).dataSource(url, username, password).schemas(schema)
                        .defaultSchema(schema).locations("classpath:db/migration").target("22").load();
                assertEquals(1, latest.migrate().migrationsExecuted);
                latest.validate(); assertEquals(0, latest.migrate().migrationsExecuted);
                try (var rows = sql.executeQuery("SELECT id,floor,room,display_name FROM locations WHERE location_code='OLD-COM1'")) {
                    assertTrue(rows.next()); assertEquals(999, rows.getLong(1)); assertEquals("2", rows.getString(2));
                    assertEquals("ROOM_SECRET", rows.getString(3)); assertEquals("Existing COM1 room", rows.getString(4));
                }
                try (var query = connection.prepareStatement("SELECT active,display_name FROM locations WHERE location_code=?")) {
                    query.setString(1, firstCode);
                    try (var rows = query.executeQuery()) {assertTrue(rows.next());assertFalse(rows.getBoolean(1));assertEquals("Previously disabled",rows.getString(2));}
                }
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM locations WHERE location_code LIKE 'NUS-%'")) {
                    assertTrue(rows.next()); assertEquals(buildings.size(), rows.getLong(1));
                }
                for (var building : buildings) {
                    String code = "NUS-" + building.get("id").asText().substring(4);
                    if (code.equals(firstCode)) continue;
                    try (var query = connection.prepareStatement("SELECT building,floor,room,active FROM locations WHERE location_code=?")) {
                        query.setString(1, code);
                        try (var rows = query.executeQuery()) {assertTrue(rows.next());assertEquals(building.get("name").asText(),rows.getString(1));assertNull(rows.getString(2));assertNull(rows.getString(3));assertTrue(rows.getBoolean(4));}
                    }
                }
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM facilities")) {assertTrue(rows.next());assertEquals(0,rows.getLong(1));}
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM maintenance_requests")) {assertTrue(rows.next());assertEquals(0,rows.getLong(1));}
            } finally {sql.execute("DROP SCHEMA " + schema + " CASCADE");}
        }
    }
    private String required(String key) {String value=System.getenv(key);assertNotNull(value,key+" required");return value;}
}
