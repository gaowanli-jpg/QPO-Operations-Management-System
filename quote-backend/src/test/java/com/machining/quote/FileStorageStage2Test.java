package com.machining.quote;

import com.machining.quote.entity.SystemFileStorageConfig;
import com.machining.quote.mapper.SystemFileStorageConfigMapper;
import com.machining.quote.storage.FileSaveCommand;
import com.machining.quote.storage.FileStorageConfig;
import com.machining.quote.storage.FileStorageConfigService;
import com.machining.quote.storage.FileStorageRouter;
import com.machining.quote.storage.LocalFileStorageProvider;
import com.machining.quote.storage.MinioFileStorageProvider;
import com.machining.quote.storage.StorageTestResult;
import com.machining.quote.storage.StorageType;
import com.machining.quote.storage.StoredFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileStorageStage2Test {
    @TempDir
    Path tempDir;

    @Test
    void storageTypeParsesStableCodes() {
        assertThat(StorageType.fromCode(" local ")).isEqualTo(StorageType.LOCAL);
        assertThat(StorageType.fromCode("MINIO")).isEqualTo(StorageType.MINIO);

        assertThatThrownBy(() -> StorageType.fromCode("ftp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported storage type");
    }

    @Test
    void configServiceLoadsActiveConfigAndRedactsSecrets() {
        SystemFileStorageConfigMapper mapper = mock(SystemFileStorageConfigMapper.class);
        SystemFileStorageConfig entity = new SystemFileStorageConfig();
        entity.setId(12L);
        entity.setName("本机 MinIO");
        entity.setStorageType("MINIO");
        entity.setActive(true);
        entity.setEnabled(true);
        entity.setConfigJson(Map.of(
                "endpoint", "http://127.0.0.1:9000",
                "bucket", "qpo-drawings",
                "accessKey", "qpo_minio_admin",
                "accessSecret", "secret-value",
                "region", "us-east-1",
                "pathStyle", true,
                "publicReadEnabled", true,
                "publicBaseUrl", "http://127.0.0.1:9000/qpo-drawings"
        ));
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(entity);

        FileStorageConfigService service = new FileStorageConfigService(mapper);
        FileStorageConfig config = service.activeConfig();

        assertThat(config.id()).isEqualTo(12L);
        assertThat(config.storageType()).isEqualTo(StorageType.MINIO);
        assertThat(config.bucket()).isEqualTo("qpo-drawings");
        assertThat(config.accessSecret()).isEqualTo("secret-value");
        assertThat(service.redactedConfig(config))
                .containsEntry("storageType", "MINIO")
                .containsEntry("accessKey", "qpo_minio_admin")
                .containsEntry("accessSecret", "******")
                .containsEntry("bucket", "qpo-drawings");
    }

    @Test
    void localProviderSavesReadsTestsAndDeletesFiles() throws Exception {
        FileStorageConfig config = FileStorageConfig.local(1L, "Local", tempDir.toString(), "http://qpo.local/files");
        LocalFileStorageProvider provider = new LocalFileStorageProvider();
        byte[] payload = "hello-local".getBytes(StandardCharsets.UTF_8);

        StoredFile stored = provider.save(new FileSaveCommand(
                config,
                "客户/RFQ-001/图纸 A.pdf",
                new ByteArrayInputStream(payload),
                payload.length,
                "application/pdf",
                "图纸 A.pdf"
        ));

        assertThat(stored.storageType()).isEqualTo(StorageType.LOCAL);
        assertThat(stored.objectKey()).isEqualTo("客户/RFQ-001/图纸 A.pdf");
        assertThat(provider.exists(config, stored.objectKey())).isTrue();
        assertThat(provider.open(config, stored.objectKey()).getContentAsByteArray()).isEqualTo(payload);
        assertThat(provider.externalReadUrl(config, stored.objectKey()))
                .hasValueSatisfying(url -> assertThat(url)
                        .contains("http://qpo.local/files/")
                        .contains("%E5%AE%A2%E6%88%B7/RFQ-001/%E5%9B%BE%E7%BA%B8%20A.pdf"));

        StorageTestResult testResult = provider.test(config);
        assertThat(testResult.success()).isTrue();

        provider.delete(config, stored.objectKey());
        assertThat(Files.exists(tempDir.resolve("客户").resolve("RFQ-001").resolve("图纸 A.pdf"))).isFalse();
    }

    @Test
    void minioProviderBuildsLongLivedPublicUrlsWithoutNetwork() {
        FileStorageConfig config = FileStorageConfig.minio(
                2L,
                "本机 MinIO",
                "http://127.0.0.1:9000",
                "qpo-drawings",
                "qpo_minio_admin",
                "secret-value",
                "us-east-1",
                true,
                true,
                "http://127.0.0.1:9000/qpo-drawings"
        );

        assertThat(new MinioFileStorageProvider().externalReadUrl(config, "客户/RFQ-001/图纸 A.pdf"))
                .hasValueSatisfying(url -> assertThat(url)
                        .contains("http://127.0.0.1:9000/qpo-drawings/")
                        .contains("%E5%AE%A2%E6%88%B7/RFQ-001/%E5%9B%BE%E7%BA%B8%20A.pdf"));
    }

    @Test
    void routerUsesActiveConfigAndMatchingProvider() throws Exception {
        FileStorageConfig config = FileStorageConfig.local(1L, "Local", tempDir.toString(), "http://qpo.local/files");
        SystemFileStorageConfigMapper mapper = mock(SystemFileStorageConfigMapper.class);
        SystemFileStorageConfig entity = new SystemFileStorageConfig();
        entity.setId(1L);
        entity.setName("Local");
        entity.setStorageType("LOCAL");
        entity.setActive(true);
        entity.setEnabled(true);
        entity.setConfigJson(Map.of("rootDir", tempDir.toString(), "publicBaseUrl", "http://qpo.local/files"));
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(entity);
        when(mapper.selectById(1L)).thenReturn(entity);

        FileStorageConfigService configService = new FileStorageConfigService(mapper);
        FileStorageRouter router = new FileStorageRouter(configService, List.of(new LocalFileStorageProvider()));
        byte[] payload = "router".getBytes(StandardCharsets.UTF_8);

        StoredFile stored = router.save(new FileSaveCommand(
                config,
                "router/test.txt",
                new ByteArrayInputStream(payload),
                payload.length,
                "text/plain",
                "test.txt"
        ));

        assertThat(stored.storageType()).isEqualTo(StorageType.LOCAL);
        assertThat(router.open(StorageType.LOCAL, 1L, "router/test.txt").getContentAsByteArray()).isEqualTo(payload);
    }
}
