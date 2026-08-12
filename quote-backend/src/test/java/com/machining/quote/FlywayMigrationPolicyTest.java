package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationPolicyTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__.+\\.sql$");
    private static final int FIRST_POST_BASELINE_VERSION = 5;

    @Test
    void activeMigrationsDoNotReuseCompressedHistoryVersions() throws IOException {
        List<MigrationFile> migrations = migrationFiles();

        assertThat(migrations).extracting(MigrationFile::version).contains(1, 2);
        assertThat(migrations).extracting(MigrationFile::version).doesNotHaveDuplicates();
        assertThat(migrations)
                .filteredOn(migration -> migration.version() > 2)
                .allSatisfy(migration -> assertThat(migration.version())
                        .as("%s must not reuse compressed-history versions V3/V4", migration.fileName())
                        .isGreaterThanOrEqualTo(FIRST_POST_BASELINE_VERSION));
    }

    @Test
    void migrationPolicyScriptsAndDocsPreventComposeTimeSurprises() throws IOException {
        Path newMigrationScript = ROOT.resolve(Path.of("scripts", "new-flyway-migration.ps1"));
        Path migrationShortcutScript = ROOT.resolve("mig.ps1");
        Path checkScript = ROOT.resolve(Path.of("scripts", "check-flyway-migrations.ps1"));
        Path composeScript = ROOT.resolve(Path.of("scripts", "compose-up.ps1"));
        Path composeFile = ROOT.resolve("docker-compose.yml");
        Path policyDoc = ROOT.resolve(Path.of("docs", "ops", "flyway-migration-policy.md"));

        assertThat(newMigrationScript).exists();
        assertThat(migrationShortcutScript).exists();
        assertThat(checkScript).exists();
        assertThat(composeScript).exists();
        assertThat(composeFile).exists();
        assertThat(policyDoc).exists();

        String newMigration = read(newMigrationScript);
        String migrationShortcut = read(migrationShortcutScript);
        String check = read(checkScript);
        String compose = read(composeScript);
        String composeYaml = read(composeFile);
        String doc = read(policyDoc);

        assertThat(newMigration)
                .contains("Position = 0")
                .contains("flyway_schema_history")
                .contains("quote-postgres")
                .contains("FIRST_POST_BASELINE_VERSION")
                .contains("[System.IO.File]::WriteAllText")
                .contains("[System.Text.UTF8Encoding]::new($false)");
        assertThat(migrationShortcut)
                .contains("scripts", "new-flyway-migration.ps1")
                .contains("-ExecutionPolicy", "Bypass")
                .contains("DryRun");
        assertThat(check)
                .contains("compressed-history")
                .contains("V3/V4")
                .contains("flyway_schema_history")
                .contains("exit 1");
        assertThat(compose)
                .contains("check-flyway-migrations.ps1")
                .contains("--env-file")
                .contains("erp.env");
        assertThat(composeYaml)
                .contains("Standard development and deployment startup")
                .contains("docker compose --env-file erp.env up -d --build")
                .contains("compose-up.ps1");
        assertThat(doc)
                .contains("V3/V4")
                .contains("V5")
                .contains("docker compose --env-file erp.env up -d --build")
                .contains("./mig.ps1 u9_customer_sync_schedule_config")
                .contains("scripts/new-flyway-migration.ps1")
                .contains("scripts/compose-up.ps1");
    }

    private static List<MigrationFile> migrationFiles() throws IOException {
        List<MigrationFile> migrations = new ArrayList<>();
        try (var paths = Files.list(MIGRATION_DIR)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String fileName = path.getFileName().toString();
                Matcher matcher = VERSION_PATTERN.matcher(fileName);
                if (matcher.matches()) {
                    migrations.add(new MigrationFile(Integer.parseInt(matcher.group(1)), fileName));
                }
            }
        }
        return migrations;
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private record MigrationFile(int version, String fileName) {
    }
}
