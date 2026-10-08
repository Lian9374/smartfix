package com.smartfix.dispatch;

import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Runs the real technician access and revocation cases against actual Flyway migrations and PostgreSQL. */
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=6"})
class TechnicianAccessPostgresIT extends TechnicianAccessIT {
    private static final String SCHEMA = "smartfix_b_tech_access_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) throws Exception {
        String url = required("TEST_DB_URL");
        String user = required("TEST_DB_USERNAME");
        String password = required("TEST_DB_PASSWORD");
        assertThat(url).startsWith("jdbc:postgresql:");
        try (var connection = DriverManager.getConnection(url, user, password);
                var statement = connection.createStatement()) {
            assertThat(connection.getCatalog()).endsWith("_test");
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        properties.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        properties.add("spring.datasource.username", () -> user);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @AfterAll
    static void removeTestSchema() throws Exception {
        try (var connection = DriverManager.getConnection(required("TEST_DB_URL"), required("TEST_DB_USERNAME"), required("TEST_DB_PASSWORD"));
                var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        assertThat(value).as(name + " is required for -Ppostgres-it").isNotBlank();
        return value;
    }
}
