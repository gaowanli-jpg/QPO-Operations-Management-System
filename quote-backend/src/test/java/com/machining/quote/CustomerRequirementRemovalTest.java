package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerRequirementRemovalTest {
    @Test
    void backendRuntimeSchemaAndActiveDocsNoLongerExposeLegacyCustomerRequirementColumn() throws Exception {
        String source = readAll(
                Path.of("src/main/java"),
                Path.of("src/main/resources/db/migration"),
                Path.of("../docs")
        );

        assertThat(source).doesNotContain("customer_requirement");
        assertThat(source)
                .contains("customerRequirements")
                .contains("MesCustomerRequirementsAssembler");
    }

    private String readAll(Path... roots) throws IOException {
        StringBuilder result = new StringBuilder();
        for (Path root : roots) {
            if (Files.isDirectory(root)) {
                try (var stream = Files.walk(root)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".java")
                                    || path.toString().endsWith(".sql")
                                    || path.toString().endsWith(".md"))
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
