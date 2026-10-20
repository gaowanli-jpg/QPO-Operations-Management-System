package com.machining.quote;

import com.machining.quote.entity.SystemFileObject;
import com.machining.quote.mapper.SystemFileObjectMapper;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.OrderFilePathService;
import com.machining.quote.storage.FileStorageRouter;
import com.machining.quote.storage.StorageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileStorageStage4Test {
    @TempDir
    Path tempDir;

    @Test
    void externalReadableUrlUsesStoredExternalUrlWhenPresent() {
        FileStorageRouter router = mock(FileStorageRouter.class);
        SystemFileObjectMapper objectMapper = mock(SystemFileObjectMapper.class);
        SystemFileObject object = fileObject();
        object.setExternalUrl("http://minio-public/qpo-drawings/ACME/RFQ-001/A.pdf");
        when(objectMapper.selectOne(any())).thenReturn(object);
        BusinessFileService service = service(router, objectMapper);

        String url = service.externalReadableUrl("/uploads/ACME/RFQ-001/A.pdf");

        assertThat(url).isEqualTo("http://minio-public/qpo-drawings/ACME/RFQ-001/A.pdf");
        verifyNoInteractions(router);
    }

    @Test
    void externalReadableUrlGeneratesUrlFromOriginalStorageConfigWhenMetadataHasNoExternalUrl() {
        FileStorageRouter router = mock(FileStorageRouter.class);
        SystemFileObjectMapper objectMapper = mock(SystemFileObjectMapper.class);
        when(objectMapper.selectOne(any())).thenReturn(fileObject());
        when(router.externalReadUrl(StorageType.MINIO, 2L, "ACME/RFQ-001/A.pdf"))
                .thenReturn(Optional.of("http://minio-public/qpo-drawings/ACME/RFQ-001/A.pdf"));
        BusinessFileService service = service(router, objectMapper);

        String url = service.externalReadableUrl("/uploads/ACME/RFQ-001/A.pdf");

        assertThat(url).isEqualTo("http://minio-public/qpo-drawings/ACME/RFQ-001/A.pdf");
        verify(router).externalReadUrl(StorageType.MINIO, 2L, "ACME/RFQ-001/A.pdf");
    }

    @Test
    void externalReadableUrlFallsBackToQpoPublicBaseForLegacyLocalFilesWithoutMetadata() {
        FileStorageRouter router = mock(FileStorageRouter.class);
        SystemFileObjectMapper objectMapper = mock(SystemFileObjectMapper.class);
        when(objectMapper.selectOne(any())).thenReturn(null);
        BusinessFileService service = service(router, objectMapper);

        String url = service.externalReadableUrl("/uploads/ACME/RFQ-001/legacy.pdf");

        assertThat(url).isEqualTo("http://qpo.local/uploads/ACME/RFQ-001/legacy.pdf");
        verifyNoInteractions(router);
    }

    @Test
    void externalReadableUrlKeepsAlreadyExternalNonUploadUrls() {
        FileStorageRouter router = mock(FileStorageRouter.class);
        SystemFileObjectMapper objectMapper = mock(SystemFileObjectMapper.class);
        BusinessFileService service = service(router, objectMapper);

        String url = service.externalReadableUrl("http://cdn.example.com/drawings/A.pdf");

        assertThat(url).isEqualTo("http://cdn.example.com/drawings/A.pdf");
        verifyNoInteractions(objectMapper, router);
    }

    private BusinessFileService service(FileStorageRouter router, SystemFileObjectMapper objectMapper) {
        OrderFilePathService pathService = new OrderFilePathService();
        ReflectionTestUtils.setField(pathService, "uploadDir", tempDir.toString());
        BusinessFileService service = new BusinessFileService(pathService, router, objectMapper);
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://qpo.local/");
        return service;
    }

    private SystemFileObject fileObject() {
        SystemFileObject object = new SystemFileObject();
        object.setPublicPath("/uploads/ACME/RFQ-001/A.pdf");
        object.setStorageType("MINIO");
        object.setStorageConfigId(2L);
        object.setObjectKey("ACME/RFQ-001/A.pdf");
        object.setOriginalFilename("A.pdf");
        object.setContentType("application/pdf");
        object.setSizeBytes(3L);
        return object;
    }
}
