package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessMasterStage12LegacyRemovalTest {
    private static final Path BACKEND_MAIN = Path.of("src", "main");
    private static final Path FRONTEND_SRC = Path.of("..", "quote-frontend", "src");

    @Test
    void runtimeCodeNoLongerDependsOnLegacyDictProcessCrudOrMapper() throws IOException {
        String backendRuntime = readAll(BACKEND_MAIN.resolve("java"));
        String dictController = Files.readString(BACKEND_MAIN.resolve(Path.of(
                "java", "com", "machining", "quote", "controller", "DictController.java")), StandardCharsets.UTF_8);
        String frontendRuntime = readAll(FRONTEND_SRC);

        assertThat(BACKEND_MAIN.resolve(Path.of("java", "com", "machining", "quote", "entity", "DictProcess.java")))
                .doesNotExist();
        assertThat(BACKEND_MAIN.resolve(Path.of("java", "com", "machining", "quote", "mapper", "DictProcessMapper.java")))
                .doesNotExist();
        assertThat(backendRuntime)
                .doesNotContain(
                        "DictProcessMapper",
                        "import com.machining.quote.entity.DictProcess;",
                        "new QueryWrapper<DictProcess>",
                        "DICT_PROCESS_CREATE",
                        "DICT_PROCESS_UPDATE",
                        "DICT_PROCESS_DELETE",
                        "\"dict_process\"");
        assertThat(dictController)
                .doesNotContain(
                        "@GetMapping(\"/processes\")",
                        "@PostMapping(\"/processes\")",
                        "@PutMapping(\"/processes/{id}\")",
                        "@DeleteMapping(\"/processes/{id}\")");
        assertThat(frontendRuntime)
                .doesNotContain(
                        "listProcesses =",
                        "saveProcess =",
                        "deleteProcess =",
                        "/api/dict/processes");
    }

    @Test
    void schemaNoLongerCreatesLegacyDictProcessOrTieredPriceFields() throws IOException {
        String schema = Files.readString(BACKEND_MAIN.resolve(Path.of("resources", "db", "migration", "V1__qpo_baseline_schema.sql")), StandardCharsets.UTF_8);

        assertThat(schema)
                .doesNotContain(
                        "CREATE TABLE dict_process (",
                        "INSERT INTO dict_process (",
                        "idx_dict_process_name",
                        "price_1_5",
                        "price_5_10",
                        "price_10_50",
                        "price_50_100",
                        "price_100_500",
                        "price_500_1000",
                        "price_1000_3000",
                        "price_3000_plus")
                .contains(
                        "CREATE TABLE dict_process_category",
                        "CREATE TABLE dict_process_detail");
    }

    private static String readAll(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            StringBuilder builder = new StringBuilder();
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                if (name.endsWith(".test.js") || name.endsWith("Test.java") || name.endsWith("Tests.java")) {
                    continue;
                }
                if (name.endsWith(".java") || name.endsWith(".js") || name.endsWith(".vue")) {
                    builder.append("\n--- ").append(path).append(" ---\n");
                    builder.append(Files.readString(path, StandardCharsets.UTF_8));
                }
            }
            return builder.toString();
        }
    }
}
