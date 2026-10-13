package com.smartfix.facility;

import org.junit.jupiter.api.AfterAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.DriverManager;
import java.util.UUID;

/** Run the same map/security/query contract with real migrations in a disposable PG schema. */
class CampusMapPostgresIT extends CampusMapIntegrationIT {
    private static final String SCHEMA = "userd_map_" + UUID.randomUUID().toString().replace("-", "");
    private static String required(String key) {
        var value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " required");
        return value;
    }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = required("TEST_DB_URL");
        if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):\\d+/[a-zA-Z0-9_]+_test")) {
            throw new IllegalArgumentException("A dedicated local *_test database is required.");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> required("TEST_DB_USERNAME"));
        properties.add("spring.datasource.password", () -> required("TEST_DB_PASSWORD"));
        properties.add("spring.datasource.hikari.schema", () -> SCHEMA);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.flyway.enabled", () -> true);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }
    @AfterAll
    static void removeSchema() throws Exception {
        try (var connection = DriverManager.getConnection(required("TEST_DB_URL"),
                required("TEST_DB_USERNAME"), required("TEST_DB_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }
}
