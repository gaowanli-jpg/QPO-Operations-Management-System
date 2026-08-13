package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MesRealBatchStage8AcceptanceTest {
    private static final Path SCRIPT = Path.of("..", "scripts", "mes-stage8-e2e.ps1");
    private static final String MATERIAL_PATH = "/api/v1/mes/openapi/material-preparation/create";
    private static final String PRODUCTION_PATH = "/api/v1/mes/openapi/demand/create";

    @Test
    void stageEightProvidesRepeatableE2eAcceptanceScriptWithSafeDefaults() throws IOException {
        String script = read(SCRIPT);

        assertThat(script)
                .contains(
                        "QPO-MES-E2E-",
                        "[switch]$ExecuteExternalWrites",
                        "New-E2eRunId",
                        "Invoke-DataCenterTokenProbe",
                        "New-RetainedQpoMesE2eData",
                        "Invoke-QpoMesMaterialBatch",
                        "Invoke-QpoMesProductionBatch",
                        "Assert-NoSensitiveEvidence",
                        "PSObject.Properties[\"task\"]",
                        "PSObject.Properties[\"id\"]",
                        "\"sortNo\":1",
                        "\"unitWorkHours\":1.5",
                        "\"processCode\":\"CNC\"",
                        MATERIAL_PATH,
                        PRODUCTION_PATH,
                        "MES material writes are disabled unless -ExecuteExternalWrites is supplied")
                .doesNotContain("QPO-MES-E2E-*")
                .doesNotContain("Write-Host $token", "Write-Output $token", "client_secret=", "Authorization: Bearer ");
    }

    @Test
    void stageEightCompletionRecordClosesRegressionCommandsAndRealMesMatrix() throws IOException {
        String doc = read(stage44Doc());

        assertThat(doc)
                .contains(
                        "2026-08-12 阶段 8 完成记录",
                        "scripts/mes-stage8-e2e.ps1",
                        ".\\mvnw.cmd test",
                        "npm test",
                        "npm run build",
                        "docker compose --env-file erp.env up -d --build",
                        "docker compose --env-file erp.env ps",
                        "QPO-MES-E2E-",
                        "数据中心 token 获取成功，日志不出现 token",
                        "QPO 图纸 URL 可从 MES 测试服务器访问",
                        "MES -> 数据中心 -> QPO",
                        "不新增用户权限",
                        "不包含完整请求、响应、token、Authorization、Cookie、client secret 或服务器凭据")
                .doesNotContain("QPO-MES-E2E-*");
    }

    @Test
    void stageEightArtifactsDoNotContainRealSecrets() throws IOException {
        String content = read(SCRIPT) + "\n" + read(stage44Doc());

        assertThat(content)
                .doesNotContain("Authorization: Bearer " + "eyJ", "access_token=" + "eyJ")
                .doesNotContainPattern("(?i)(client_secret|access_token|authorization|cookie|password)\\s*[:=]\\s*[A-Za-z0-9+/=_\\-.]{12,}")
                .doesNotContainPattern("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{12,}");
    }

    private static Path stage44Doc() throws IOException {
        try (Stream<Path> stream = Files.walk(Path.of("..", "docs"))) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("44-"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing MES stage 44 development document"));
        }
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

}
