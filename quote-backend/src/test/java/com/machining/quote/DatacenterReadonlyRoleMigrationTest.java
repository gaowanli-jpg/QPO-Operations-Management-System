package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DatacenterReadonlyRoleMigrationTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path MIGRATION = ROOT.resolve("quote-backend/src/main/resources/db/migration/V15__data_center_readonly_role.sql");

    @Test
    void migrationCreatesReadonlyRoleAndUsesPasswordPlaceholder() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("qpo_datacenter_ro")
                .contains("POSTGRES_DATACENTER_RO_PASSWORD")
                .contains("GRANT CONNECT ON DATABASE")
                .contains("GRANT USAGE ON SCHEMA public")
                .contains("GRANT pg_read_all_data TO qpo_datacenter_ro")
                .contains("GRANT SELECT ON ALL TABLES IN SCHEMA public TO qpo_datacenter_ro")
                .contains("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO qpo_datacenter_ro")
                .contains("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO qpo_datacenter_ro")
                .contains("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO qpo_datacenter_ro");
    }
}
