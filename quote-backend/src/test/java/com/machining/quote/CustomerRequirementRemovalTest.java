package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerRequirementRemovalTest {
    private static final List<String> REMOVED_TOKENS = List.of(
            "customer" + "Requirement",
            "customer" + "_requirement",
            "customer" + "Requirements",
            "客" + "户" + "需" + "求"
    );

    @Test
    void backendRuntimeSchemaAndActiveDocsNoLongerExposeCustomerRequirement() throws Exception {
        String source = readAll(
                Path.of("src/main/java"),
                Path.of("src/main/resources/db/migration"),
                Path.of("../docs/QPO-MES-WMS对接/06-提供给MES的物料号接口对接说明.md"),
                Path.of("../docs/QPO-MES-WMS对接/08-MES物料号回调接口对接说明.md"),
                Path.of("../docs/QPO报价状态流转/37-RFQ议价提单派生销售订单并接入U9开发文档.md")
        );

        for (String token : REMOVED_TOKENS) {
            assertThat(source).doesNotContain(token);
        }
    }

    private String readAll(Path... roots) throws IOException {
        StringBuilder result = new StringBuilder();
        for (Path root : roots) {
            if (Files.isDirectory(root)) {
                try (var stream = Files.walk(root)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".sql"))
                            .sorted()
                            .forEach(path -> append(result, path));
                }
            } else {
                append(result, root);
            }
        }
        return result.toString();
    }

    private void append(StringBuilder result, Path path) {
        try {
            result.append("\n--- ").append(path).append("\n")
                    .append(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}
