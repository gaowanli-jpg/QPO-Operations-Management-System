package com.machining.quote;

import com.machining.quote.config.FlywayCompressedHistoryMigrationConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlywayCompressedHistoryMigrationStrategyTest {

    @Test
    void repairsCompressedHistoryV3V4BeforeMigrating() {
        Flyway flyway = flywayWithInfo(missing("3"), missing("4"));
        FlywayMigrationStrategy strategy = new FlywayCompressedHistoryMigrationConfig().flywayMigrationStrategy();

        strategy.migrate(flyway);

        verify(flyway).repair();
        verify(flyway).migrate();
    }

    @Test
    void rejectsUnexpectedMissingMigrationsInsteadOfMaskingDeletedFiles() {
        Flyway flyway = flywayWithInfo(missing("6"));
        FlywayMigrationStrategy strategy = new FlywayCompressedHistoryMigrationConfig().flywayMigrationStrategy();

        assertThatThrownBy(() -> strategy.migrate(flyway))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("V6");

        verify(flyway, never()).repair();
        verify(flyway, never()).migrate();
    }

    @Test
    void migratesDirectlyWhenNoMissingMigrationExists() {
        Flyway flyway = flywayWithInfo(success("1"), success("2"), success("5"));
        FlywayMigrationStrategy strategy = new FlywayCompressedHistoryMigrationConfig().flywayMigrationStrategy();

        strategy.migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }

    private static Flyway flywayWithInfo(MigrationInfo... migrationInfos) {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService infoService = mock(MigrationInfoService.class);
        when(infoService.all()).thenReturn(migrationInfos);
        when(flyway.info()).thenReturn(infoService);
        return flyway;
    }

    private static MigrationInfo missing(String version) {
        return info(version, MigrationState.MISSING_SUCCESS);
    }

    private static MigrationInfo success(String version) {
        return info(version, MigrationState.SUCCESS);
    }

    private static MigrationInfo info(String version, MigrationState state) {
        MigrationInfo info = mock(MigrationInfo.class);
        when(info.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
        when(info.getState()).thenReturn(state);
        return info;
    }
}
