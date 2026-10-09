package com.smartfix.request;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartfix.SmartFixApplication;
import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAuthenticationData;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

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
                assertUpgradedDetailsPage(url, user, password, schema);
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

    private void assertUpgradedDetailsPage(String url, String username, String password,
            String schema) throws Exception {
        // Exercise the actual services and Thymeleaf page against the preserved Sprint 2 row.
        // A mock servlet context starts no HTTP server and uses only this isolated test schema.
        try (var context = new GenericWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().setActiveProfiles("test");
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "spring.datasource.url=" + url + (url.contains("?") ? "&" : "?")
                            + "currentSchema=" + schema,
                    "spring.datasource.username=" + username,
                    "spring.datasource.password=" + password,
                    "spring.datasource.driver-class-name=org.postgresql.Driver",
                    "spring.jpa.hibernate.ddl-auto=validate",
                    "spring.jpa.open-in-view=false",
                    "spring.flyway.enabled=true",
                    "spring.flyway.locations=classpath:db/migration",
                    "spring.flyway.schemas=" + schema,
                    "spring.flyway.default-schema=" + schema,
                    "smartfix.bootstrap-admin.enabled=false");
            // Keep other tests' route probes and datasource configurations out of this context.
            context.getBeanFactory().registerSingleton("upgradePageTestExclusions", new TypeExcludeFilter() {
                @Override
                public boolean match(MetadataReader reader, MetadataReaderFactory factory) {
                    return reader.getClassMetadata().getClassName().matches(".*(Test|Tests|IT)(\\$.*)?");
                }
            });
            new AnnotatedBeanDefinitionReader(context).register(SmartFixApplication.class);
            context.refresh();
            var requester = new SmartFixUserDetails(new UserAuthenticationData(
                    1L, "test.requester", "synthetic-test-hash", Role.REQUESTER,
                    AccountStatus.ACTIVE, 0));
            MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                    .perform(get("/requests/SF-2026-000001").with(user(requester)))
                    .andExpect(status().isOk())
                    .andExpect(view().name("request/detail"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Broken light")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Test location")));
        }
    }

    private String required(String key) {
        String value = System.getenv(key);
        assertThat(value).as(key).isNotBlank();
        return value;
    }
}
