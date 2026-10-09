package com.smartfix.common.configuration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Set;

/** Explicit one-time recovery only; normal startups retain strict ordered validation. */
@Configuration
@ConditionalOnProperty(name = "smartfix.database.reconcile-legacy-migrations", havingValue = "true")
public class LegacyMigrationRecoveryConfiguration {
    @Bean
    public FlywayMigrationStrategy recoverPublishedBranchMigrations() {
        return flyway -> {
            var ignored = Arrays.stream(flyway.info().all())
                    .filter(info -> info.getState() == MigrationState.IGNORED).toList();
            Set<String> allowed = Set.of("10", "11", "14", "16");
            if (ignored.stream().anyMatch(info -> info.getVersion() == null || !allowed.contains(info.getVersion().toString())))
                throw new FlywayException("Unexpected older migrations: manual review required.");
            if (ignored.isEmpty()) flyway.migrate();
            else Flyway.configure().configuration(flyway.getConfiguration()).outOfOrder(true).load().migrate();
            // No ignored migrations/checksums: validate again using the ordinary configuration.
            flyway.validate();
        };
    }
}
