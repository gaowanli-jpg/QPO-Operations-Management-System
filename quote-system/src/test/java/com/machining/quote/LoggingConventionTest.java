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
        assertHasLogger("controller/UploadFileController.java", "UploadFileController");
        assertHasLogger("service/EngineerService.java", "EngineerService");
        assertHasLogger("service/SystemTaskService.java", "SystemTaskService");
        assertHasLogger("service/QuoteTemplateService.java", "QuoteTemplateService");
        assertHasLogger("service/AuditLogRetentionService.java", "AuditLogRetentionService");
        assertHasLogger("service/AuditService.java", "AuditService");
        assertHasLogger("service/JwtService.java", "JwtService");
        assertHasLogger("service/LeadTimeService.java", "LeadTimeService");
        assertHasLogger("service/OrderManagementService.java", "OrderManagementService");
        assertHasLogger("service/DeliveryService.java", "DeliveryService");
        assertHasLogger("service/QuoteProcessService.java", "QuoteProcessService");
        assertHasLogger("service/InternalTargetLeadTimeService.java", "InternalTargetLeadTimeService");
        assertHasLogger("service/StorageInitializer.java", "StorageInitializer");
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

    @Test
    void remainingBusinessServicesKeepMainFlowLogEvents() throws IOException {
        assertThat(source("service/QuoteTemplateService.java"))
                .contains("Quote template upload started")
                .contains("Quote template validation failed")
                .contains("Quote template upload completed")
                .contains("log.error(\"Quote template upload failed");

        assertThat(source("service/OrderManagementService.java"))
                .contains("Order production reset completed")
                .contains("Order supplement drawings completed")
                .contains("Order delete completed");

        assertThat(source("service/DeliveryService.java"))
                .contains("Delivery ticket generation started")
                .contains("Delivery ticket generation completed")
                .contains("Delivery ticket generation failed")
                .contains("log.error(\"Delivery ticket generation failed");

        assertThat(source("service/QuoteProcessService.java"))
                .contains("Quote lead time shifted")
                .contains("Quote lead time set")
                .contains("Quote sheet save started")
                .contains("Quote sheet save completed")
                .contains("Quote sheet save rejected")
                .contains("Quote sheet save failed")
                .contains("log.error(\"Quote sheet save failed");

        assertThat(source("service/InternalTargetLeadTimeService.java"))
                .contains("Internal target lead time shifted")
                .contains("Internal target lead time set");
    }

    @Test
    void retentionAndSupportServicesKeepLowNoiseLogEvents() throws IOException {
        assertThat(source("service/AuditLogRetentionService.java"))
                .contains("Audit log cleanup started")
                .contains("Audit log cleanup completed")
                .contains("Audit log cleanup failed")
                .contains("log.error(\"Audit log cleanup failed");

        assertThat(source("controller/UploadFileController.java"))
                .contains("Upload file access rejected")
                .contains("Upload file missing")
                .contains("Upload file read failed")
                .contains("log.error(\"Upload file read failed");

        assertThat(source("service/AuditService.java"))
                .contains("Audit log record failed")
                .contains("log.error(\"Audit log record failed");

        assertThat(source("service/JwtService.java"))
                .contains("JWT parse failed")
                .doesNotContain("token={}");

        assertThat(source("service/LeadTimeService.java"))
                .contains("Lead time parse failed");

        assertThat(source("service/StorageInitializer.java"))
                .contains("Storage directories initialized")
                .contains("Storage directory initialization failed")
                .contains("log.error(\"Storage directory initialization failed");
    }

    @Test
    void healthCheckControllerDoesNotAddBusinessLogs() throws IOException {
        String source = source("controller/HealthController.java");

        assertThat(source)
                .doesNotContain("LoggerFactory.getLogger")
                .doesNotContain("log.info")
                .doesNotContain("log.warn")
                .doesNotContain("log.error");
    }

    @Test
    void operationsGuideDocumentsMainFlowLogTroubleshooting() throws IOException {
        String guide = Files.readString(
                Path.of("..", "docs", "QPO日志模块", "03-日志运维说明.md"),
                StandardCharsets.UTF_8
        );

        assertThat(guide)
                .contains("主流程文件日志事件命名")
                .contains("grep -R 'RFQ import")
                .contains("grep -R 'taskId=")
                .contains("requestId + taskId + audit_log");
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
