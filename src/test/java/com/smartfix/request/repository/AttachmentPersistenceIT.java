package com.smartfix.request.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class AttachmentPersistenceIT {

    @Test
    void attachmentMetadataCanBePersistedForMaintenanceRequest()
            throws Exception {

        String url = required("TEST_DB_URL");
        String username = required("TEST_DB_USERNAME");
        String password = required("TEST_DB_PASSWORD");

        assertThat(url)
                .as("Use a dedicated PostgreSQL test database")
                .startsWith("jdbc:postgresql:");

        String schema =
                "smartfix_attachment_it_"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        try (Connection connection =
                     DriverManager.getConnection(
                             url,
                             username,
                             password
                     )) {

            assertThat(connection.getCatalog())
                    .as("Test database name must end in _test")
                    .endsWith("_test");

            try (var statement =
                         connection.createStatement()) {
                statement.execute(
                        "CREATE SCHEMA " + schema
                );
            }

            try {
                Flyway flyway =
                        Flyway.configure()
                                .dataSource(
                                        url,
                                        username,
                                        password
                                )
                                .schemas(schema)
                                .defaultSchema(schema)
                                .locations(
                                        "classpath:db/migration"
                                )
                                .load();

                flyway.migrate();
                flyway.validate();

                try (var statement =
                             connection.createStatement()) {

                    statement.execute(
                            "SET search_path TO " + schema
                    );

                    statement.executeUpdate("""
                            INSERT INTO users
                                (username,
                                 display_name,
                                 password_hash,
                                 role)
                            VALUES
                                ('attachment.user',
                                 'Attachment User',
                                 'synthetic-test-hash',
                                 'REQUESTER')
                            """);

                    statement.executeUpdate("""
                            INSERT INTO locations
                                (location_code,
                                 building,
                                 floor,
                                 room,
                                 display_name,
                                 active)
                            VALUES
                                ('ATTACHMENT-TEST',
                                 'Test Building',
                                 '1',
                                 '101',
                                 'Attachment Test Location',
                                 TRUE)
                            """);

                    Long requesterId =
                            queryId(
                                    statement,
                                    """
                                    SELECT id
                                    FROM users
                                    WHERE username =
                                        'attachment.user'
                                    """
                            );

                    Long locationId =
                            queryId(
                                    statement,
                                    """
                                    SELECT id
                                    FROM locations
                                    WHERE location_code =
                                        'ATTACHMENT-TEST'
                                    """
                            );

                    statement.executeUpdate("""
                            INSERT INTO maintenance_requests
                                (ticket_number,
                                 requester_id,
                                 location_id,
                                 title,
                                 description,
                                 category,
                                 urgency_level,
                                 status)
                            VALUES
                                ('SF-2026-000001',
                                 %d,
                                 %d,
                                 'Leaking pipe',
                                 'Water is leaking under the sink.',
                                 'PLUMBING',
                                 'MEDIUM',
                                 'SUBMITTED')
                            """.formatted(
                            requesterId,
                            locationId
                    ));

                    Long requestId =
                            queryId(
                                    statement,
                                    """
                                    SELECT id
                                    FROM maintenance_requests
                                    WHERE ticket_number =
                                        'SF-2026-000001'
                                    """
                            );

                    statement.executeUpdate("""
                            INSERT INTO request_attachments
                                (request_id,
                                 original_filename,
                                 stored_filename,
                                 content_type,
                                 size_bytes)
                            VALUES
                                (%d,
                                 'leaking-pipe.png',
                                 '550e8400-e29b-41d4-a716-446655440000.png',
                                 'image/png',
                                 2048)
                            """.formatted(requestId));

                    try (ResultSet result =
                                 statement.executeQuery("""
                                         SELECT
                                             request_id,
                                             original_filename,
                                             stored_filename,
                                             content_type,
                                             size_bytes
                                         FROM request_attachments
                                         """)) {

                        assertThat(result.next())
                                .isTrue();

                        assertThat(
                                result.getLong("request_id")
                        ).isEqualTo(requestId);

                        assertThat(
                                result.getString(
                                        "original_filename"
                                )
                        ).isEqualTo(
                                "leaking-pipe.png"
                        );

                        assertThat(
                                result.getString(
                                        "content_type"
                                )
                        ).isEqualTo(
                                "image/png"
                        );

                        assertThat(
                                result.getLong(
                                        "size_bytes"
                                )
                        ).isEqualTo(2048L);
                    }
                }

            } finally {
                try (var statement =
                             connection.createStatement()) {
                    statement.execute(
                            "DROP SCHEMA "
                                    + schema
                                    + " CASCADE"
                    );
                }
            }
        }
    }

    private Long queryId(
            java.sql.Statement statement,
            String sql
    ) throws Exception {

        try (ResultSet result =
                     statement.executeQuery(sql)) {

            assertThat(result.next()).isTrue();

            return result.getLong("id");
        }
    }

    private String required(String name) {
        String value = System.getenv(name);

        assertThat(value)
                .as(
                        name
                                + " is required for "
                                + "-Ppostgres-it"
                )
                .isNotBlank();

        return value;
    }
}