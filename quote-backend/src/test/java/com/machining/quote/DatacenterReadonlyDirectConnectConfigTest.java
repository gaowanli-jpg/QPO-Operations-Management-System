package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DatacenterReadonlyDirectConnectConfigTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path APP_YAML = ROOT.resolve("quote-backend/src/main/resources/application.yaml");
    private static final Path COMPOSE = ROOT.resolve("docker-compose.yml");
    private static final Path ENV = ROOT.resolve("erp.env");
    private static final Path ENV_EXAMPLE = ROOT.resolve("erp.env.example");

    @Test
    void postgresBindAndReadonlyPasswordAreWiredThroughEnvFilesAndFlywayPlaceholders() throws IOException {
        String env = read(ENV);
        String envExample = read(ENV_EXAMPLE);
        String appYaml = read(APP_YAML);
        String compose = read(COMPOSE);

        assertThat(envValue(env, "POSTGRES_BIND")).isEqualTo("0.0.0.0");
        assertThat(envValue(envExample, "POSTGRES_BIND")).isEqualTo("0.0.0.0");

        assertThat(envValue(env, "POSTGRES_DATACENTER_RO_PASSWORD")).isNotBlank();
        assertThat(envValue(envExample, "POSTGRES_DATACENTER_RO_PASSWORD")).isEmpty();

        assertThat(appYaml)
                .contains("placeholders:")
                .contains("datacenter_ro_password: ${POSTGRES_DATACENTER_RO_PASSWORD:}");
        assertThat(compose)
                .contains("POSTGRES_DATACENTER_RO_PASSWORD: ${POSTGRES_DATACENTER_RO_PASSWORD:?Set POSTGRES_DATACENTER_RO_PASSWORD in erp.env before starting}");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String envValue(String text, String key) {
        return Arrays.stream(text.split("\\R"))
                .filter(line -> line.startsWith(key + "="))
                .findFirst()
                .map(line -> line.substring(key.length() + 1))
                .orElseThrow(() -> new IllegalStateException("Missing env key: " + key));
    }
}
