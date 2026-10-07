package com.smartfix.request;

import static org.assertj.core.api.Assertions.*;

import com.smartfix.request.repository.RequestTicketSequenceRepository;
import com.smartfix.request.service.RequestTicketNumberGenerator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.*;
import java.time.*;
import java.util.UUID;

/**
 * Runs the same C acceptance cases on a migrated PostgreSQL schema; no Hibernate table creation.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.driver-class-name=org.postgresql.Driver",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.datasource.hikari.maximum-pool-size=4"
        })
class RequestWorkflowPostgresIT extends RequestWorkflowTest {
    private static final String SCHEMA =
            "smartfix_c_workflow_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry r) {
        String url = required("TEST_DB_URL"),
                user = required("TEST_DB_USERNAME"),
                password = required("TEST_DB_PASSWORD");
        try (Connection c = DriverManager.getConnection(url, user, password);
                Statement s = c.createStatement()) {
            assertThat(c.getCatalog()).endsWith("_test");
            s.execute("CREATE SCHEMA " + SCHEMA);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot prepare dedicated test schema", e);
        }
        r.add(
                "spring.datasource.url",
                () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        r.add("spring.datasource.username", () -> user);
        r.add("spring.datasource.password", () -> password);
        r.add("spring.flyway.schemas", () -> SCHEMA);
        r.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @Autowired RequestTicketSequenceRepository sequences;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void allocatesTicketsUsingTheActualPostgresCounter() {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton(
                "clock", Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC));
        var generator =
                new RequestTicketNumberGenerator(sequences, beans.getBeanProvider(Clock.class));
        var transaction = new TransactionTemplate(transactionManager);
        assertThat(transaction.<String>execute(s -> generator.nextTicketNumber()))
                .isEqualTo("SF-2026-000001");
        assertThat(transaction.<String>execute(s -> generator.nextTicketNumber()))
                .isEqualTo("SF-2026-000002");
    }

    @AfterAll
    static void removeSchema() throws Exception {
        try (Connection c =
                        DriverManager.getConnection(
                                required("TEST_DB_URL"),
                                required("TEST_DB_USERNAME"),
                                required("TEST_DB_PASSWORD"));
                Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        assertThat(value).as(name).isNotBlank();
        return value;
    }
}
