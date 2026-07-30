package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SystemStorageDeploymentStage6Test {
    private static final Path ENV_EXAMPLE = Path.of("..", "erp.env.example");
    private static final Path DOCKER_COMPOSE = Path.of("..", "docker-compose.yml");
    private static final Path DEPLOYMENT_DOC = Path.of("..", "QPO部署说明.md");

    @Test
    void envExampleDocumentsLocalDefaultAndMinioPlaceholdersWithoutSecretDefaults() throws IOException {
        String envExample = read(ENV_EXAMPLE);

        assertThat(envExample)
                .contains("ERP_FILE_STORAGE_TYPE=LOCAL")
                .contains("ERP_MINIO_ENDPOINT=")
                .contains("ERP_MINIO_BUCKET=")
                .contains("ERP_MINIO_ACCESS_KEY=")
                .contains("ERP_MINIO_SECRET_KEY=")
                .contains("ERP_MINIO_REGION=us-east-1")
                .contains("ERP_MINIO_PATH_STYLE=true")
                .contains("ERP_CONFIG_SECRET_KEY=")
                .contains("MINIO_IMAGE=minio/minio:latest")
                .contains("MINIO_ROOT_USER=qpo_minio_admin")
                .contains("MINIO_ROOT_PASSWORD=")
                .contains("MINIO_API_PORT=9000")
                .contains("MINIO_CONSOLE_PORT=9001")
                .doesNotContain("ERP_MINIO_SECRET_KEY=change")
                .doesNotContain("ERP_CONFIG_SECRET_KEY=change");
    }

    @Test
    void dockerComposeInjectsStorageEnvAndProvidesOptionalMinioProfile() throws IOException {
        String compose = read(DOCKER_COMPOSE);

        assertThat(compose)
                .contains("ERP_FILE_STORAGE_TYPE: ${ERP_FILE_STORAGE_TYPE:-LOCAL}")
                .contains("ERP_MINIO_ENDPOINT: ${ERP_MINIO_ENDPOINT:-}")
                .contains("ERP_MINIO_BUCKET: ${ERP_MINIO_BUCKET:-}")
                .contains("ERP_MINIO_ACCESS_KEY: ${ERP_MINIO_ACCESS_KEY:-}")
                .contains("ERP_MINIO_SECRET_KEY: ${ERP_MINIO_SECRET_KEY:-}")
                .contains("ERP_MINIO_REGION: ${ERP_MINIO_REGION:-us-east-1}")
                .contains("ERP_MINIO_PATH_STYLE: ${ERP_MINIO_PATH_STYLE:-true}")
                .contains("ERP_CONFIG_SECRET_KEY: ${ERP_CONFIG_SECRET_KEY:-}")
                .contains("minio:")
                .contains("profiles:")
                .contains("- minio")
                .contains("image: ${MINIO_IMAGE:-minio/minio:latest}")
                .contains("container_name: quote-minio")
                .contains("command: server /data --console-address \":9001\"")
                .contains("MINIO_ROOT_USER: ${MINIO_ROOT_USER:-qpo_minio_admin}")
                .contains("MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:?Set MINIO_ROOT_PASSWORD in erp.env before starting MinIO}")
                .contains("${MINIO_BIND:-127.0.0.1}:${MINIO_API_PORT:-9000}:9000")
                .contains("${MINIO_BIND:-127.0.0.1}:${MINIO_CONSOLE_PORT:-9001}:9001")
                .contains("minio_data:/data")
                .contains("minio_data:");
    }

    @Test
    void deploymentDocExplainsLocalDefaultMinioValidationAndUnifiedCompanyMinio() throws IOException {
        String doc = read(DEPLOYMENT_DOC);

        assertThat(doc)
                .contains("ERP_FILE_STORAGE_TYPE=LOCAL")
                .contains("docker compose --env-file erp.env --profile minio up -d minio")
                .contains("docker compose --env-file erp.env --profile minio up -d --build")
                .contains("http://localhost:9001")
                .contains("公司统一 MinIO")
                .contains("本地开发默认 LOCAL")
                .contains("日志、数据库备份不放入 MinIO");
    }

    private String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
