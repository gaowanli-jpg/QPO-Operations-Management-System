package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingConventionTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");

    @Test
    void productionCodeDoesNotUseConsoleLogging() throws IOException {
        List<Path> violations;
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, "System.out.println(", ".printStackTrace("))
                    .toList();
        }

        assertThat(violations)
                .as("Production code must use SLF4J instead of console logging")
                .isEmpty();
    }

    @Test
    void keyMainFlowClassesUseSlf4jLoggers() throws IOException {
        assertHasLogger("controller/MarketController.java", "MarketController");
        assertHasLogger("service/EngineerService.java", "EngineerService");
        assertHasLogger("service/SystemTaskService.java", "SystemTaskService");
        assertHasLogger("service/QuoteTemplateService.java", "QuoteTemplateService");
        assertHasLogger("service/AuditLogRetentionService.java", "AuditLogRetentionService");
    }

    @Test
    void logbackPatternKeepsRequestIdInFileLogs() throws IOException {
        String logback = Files.readString(MAIN_RESOURCES.resolve("logback-spring.xml"), StandardCharsets.UTF_8);

        assertThat(logback).contains("requestId=%X{requestId:-none}");
    }

    @Test
    void engineerServiceKeepsMainFlowLogEvents() throws IOException {
        String source = source("service/EngineerService.java");

        assertThat(source)
                .contains("Item quote saved")
                .contains("Process rate matched")
                .contains("Process dictionary missing")
                .contains("Process rate missing")
                .contains("Order quote refresh started")
                .contains("Order quote refresh completed")
                .contains("Order quote completed")
                .contains("Order engineer config saved");
    }

    @Test
    void systemTaskServiceKeepsTaskLifecycleLogEvents() throws IOException {
        String source = source("service/SystemTaskService.java");

        assertThat(source)
                .contains("System task submitted")
                .contains("System task started")
                .contains("System task completed")
                .contains("System task failed")
                .contains("log.error(\"System task failed");
    }

    private void assertHasLogger(String relativePath, String className) throws IOException {
        assertThat(source(relativePath)).contains("LoggerFactory.getLogger(" + className + ".class)");
    }

    private String source(String relativePath) throws IOException {
        return Files.readString(
                MAIN_JAVA.resolve(Path.of("com", "machining", "quote")).resolve(relativePath),
                StandardCharsets.UTF_8
        );
    }

    private boolean containsAny(Path path, String... needles) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            for (String needle : needles) {
                if (source.contains(needle)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}
