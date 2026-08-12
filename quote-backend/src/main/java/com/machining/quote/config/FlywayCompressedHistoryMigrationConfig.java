package com.machining.quote.config;

import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
public class FlywayCompressedHistoryMigrationConfig {
    private static final Logger log = LoggerFactory.getLogger(FlywayCompressedHistoryMigrationConfig.class);
    private static final Set<String> RESERVED_COMPRESSED_HISTORY_VERSIONS = Set.of("3", "4");

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            List<String> missingVersions = missingResolvedVersions(flyway.info().all());
            if (!missingVersions.isEmpty()) {
                if (!RESERVED_COMPRESSED_HISTORY_VERSIONS.containsAll(missingVersions)) {
                    throw new IllegalStateException("Flyway has missing applied migrations outside compressed history: "
                            + missingVersions.stream().map(version -> "V" + version).toList());
                }
                log.warn("Repairing Flyway compressed-history metadata for reserved migrations {}", missingVersions);
                flyway.repair();
            }
            flyway.migrate();
        };
    }

    private List<String> missingResolvedVersions(MigrationInfo[] migrations) {
        return java.util.Arrays.stream(migrations == null ? new MigrationInfo[0] : migrations)
                .filter(migration -> migration.getVersion() != null)
                .filter(migration -> migration.getState() != null
                        && migration.getState().name().startsWith("MISSING"))
                .map(migration -> migration.getVersion().getVersion())
                .distinct()
                .sorted()
                .toList();
    }
}
