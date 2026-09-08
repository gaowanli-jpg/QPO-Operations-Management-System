package com.machining.quote;

import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SystemFileObject;
import com.machining.quote.controller.UploadFileController;
import com.machining.quote.mapper.SystemFileObjectMapper;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.OrderFilePathService;
import com.machining.quote.service.BusinessFileService.FileReadResult;
import com.machining.quote.service.BusinessFileService.StoredBusinessFile;
import com.machining.quote.storage.FileStorageRouter;
import com.machining.quote.storage.StorageType;
import com.machining.quote.storage.StoredFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageStage3Test {
    @TempDir
    Path tempDir;

    @Test
    void businessFileServiceSavesDrawingThroughActiveStorageAndRecordsMetadata() throws Exception {
        OrderFilePathService pathService = pathService();
        FileStorageRouter router = mock(FileStorageRouter.class);
        SystemFileObjectMapper objectMapper = mock(SystemFileObjectMapper.class);
        AtomicReference<String> savedObjectKey = new AtomicReference<>();
        AtomicReference<SystemFileObject> insertedObject = new AtomicReference<>();
        when(router.saveActive(any(), any(), anyLong(), any(), any())).thenAnswer(invocation -> {
            savedObjectKey.set(invocation.getArgument(0));
            return new StoredFile(StorageType.LOCAL, 1L, invocation.getArgument(0), invocation.getArgument(4),
                    invocation.getArgument(3), invocation.getArgument(2), "http://qpo.local/files/" + invocation.getArgument(0));
        });
        when(objectMapper.insert(any(SystemFileObject.class))).thenAnswer(invocation -> {
            insertedObject.set(invocation.getArgument(0));
            return 1;
        });

        BusinessFileService service = new BusinessFileService(pathService, router, objectMapper);
        MockMultipartFile drawing = new MockMultipartFile("drawing", "DRW-001.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8));
        StoredBusinessFile stored = service.saveDrawing(order(), drawing, "RFQ_IMPORT_DRAWING", "RFQ-2026-0001");

        assertThat(stored.publicPath()).startsWith("/uploads/客户A/RFQ-2026-0001/");
        assertThat(stored.publicPath()).endsWith(".pdf");
        assertThat(savedObjectKey.get()).isEqualTo(stored.objectKey());
        assertThat(insertedObject.get().getPublicPath()).isEqualTo(stored.publicPath());
        assertThat(insertedObject.get().getStorageType()).isEqualTo("LOCAL");
        assertThat(insertedObject.get().getStorageConfigId()).isEqualTo(1L);
        assertThat(insertedObject.get().getObjectKey()).isEqualTo(stored.objectKey());
        assertThat(insertedObject.get().getOriginalFilename()).isEqualTo("DRW-001.pdf");
        assertThat(insertedObject.get().getBizType()).isEqualTo("RFQ_IMPORT_DRAWING");
        assertThat(insertedObject.get().getBizId()).isEqualTo("RFQ-2026-0001");
    }

    @Test
    void businessFileServiceSavesDrawingFolderAttachmentsUnderOneFolderPrefixAndRecordsMetadataPerFile() throws Exception {
        OrderFilePathService pathService = pathService();
        FileStorageRouter router = mock(FileStorageRouter.class);
        SystemFileObjectMapper objectMapper = mock(SystemFileObjectMapper.class);
        List<SystemFileObject> insertedObjects = new java.util.ArrayList<>();
        when(router.saveActive(any(), any(), anyLong(), any(), any())).thenAnswer(invocation ->
                new StoredFile(StorageType.MINIO, 7L, invocation.getArgument(0), invocation.getArgument(4),
                        invocation.getArgument(3), invocation.getArgument(2), null));
        when(objectMapper.insert(any(SystemFileObject.class))).thenAnswer(invocation -> {
            insertedObjects.add(invocation.getArgument(0));
            return 1;
        });

        BusinessFileService service = new BusinessFileService(pathService, router, objectMapper);
        MockMultipartFile first = new MockMultipartFile("drawingFolderFiles", "A-100.pdf", "application/pdf", "pdf1".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile second = new MockMultipartFile("drawingFolderFiles", "A-101.PDF", "application/pdf", "pdf2".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile note = new MockMultipartFile("drawingFolderFiles", "note.txt", "text/plain", "txt".getBytes(StandardCharsets.UTF_8));

        BusinessFileService.DrawingFolderSaveResult result = service.saveDrawingFolderFiles(
                "RFQ",
                "101",
                "客户A图纸包",
                List.of(first, second, note),
                "RFQ_DRAWING_FOLDER",
                "101");

        assertThat(result.folderUrl())
                .startsWith("/uploads/drawing-folders/RFQ/101/")
                .endsWith("/");
        assertThat(result.folderName()).isEqualTo("客户A图纸包");
        assertThat(result.fileCount()).isEqualTo(3);
        assertThat(result.files()).extracting(BusinessFileService.DrawingFolderFile::fileName)
                .containsExactly("A-100.pdf", "A-101.PDF", "note.txt");
        assertThat(result.files()).extracting(BusinessFileService.DrawingFolderFile::contentType)
                .containsExactly("application/pdf", "application/pdf", "text/plain");
        assertThat(result.files()).allSatisfy(file -> assertThat(file.publicPath()).startsWith(result.folderUrl()));
        assertThat(result.files().get(0).publicPath()).endsWith(".pdf");
        assertThat(result.files().get(2).publicPath()).endsWith(".txt");
        assertThat(insertedObjects).hasSize(3);
        assertThat(insertedObjects).extracting(SystemFileObject::getPublicPath)
                .allSatisfy(path -> assertThat(path).startsWith(result.folderUrl()));
        assertThat(insertedObjects).extracting(SystemFileObject::getBizType)
                .containsExactly("RFQ_DRAWING_FOLDER", "RFQ_DRAWING_FOLDER", "RFQ_DRAWING_FOLDER");
        assertThat(insertedObjects).extracting(SystemFileObject::getBizId)
                .containsExactly("101", "101", "101");
        verify(router, times(3)).saveActive(any(), any(), anyLong(), any(), any());
    }

    @Test
    void uploadControllerServesPdfsInlineButForcesOtherAttachmentsToDownload() throws Exception {
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        UploadFileController controller = new UploadFileController(businessFileService);
        when(businessFileService.openByPublicPath("/uploads/drawing-folders/RFQ/101/FOLDER/A.pdf"))
                .thenReturn(Optional.of(readResult("pdf", "application/pdf", "A.pdf")));
        when(businessFileService.openByPublicPath("/uploads/drawing-folders/RFQ/101/FOLDER/note.txt"))
                .thenReturn(Optional.of(readResult("txt", "text/plain", "note.txt")));

        MockHttpServletResponse pdfResponse = new MockHttpServletResponse();
        controller.getUploadFile(new MockHttpServletRequest("GET", "/uploads/drawing-folders/RFQ/101/FOLDER/A.pdf"), pdfResponse);

        assertThat(pdfResponse.getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline;");

        MockHttpServletResponse textResponse = new MockHttpServletResponse();
        controller.getUploadFile(new MockHttpServletRequest("GET", "/uploads/drawing-folders/RFQ/101/FOLDER/note.txt"), textResponse);

        assertThat(textResponse.getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
    }

    @Test
    void businessFileServiceListsDrawingFolderFilesByFolderPrefix() {
        OrderFilePathService pathService = pathService();
        FileStorageRouter router = mock(FileStorageRouter.class);
        SystemFileObjectMapper objectMapper = mock(SystemFileObjectMapper.class);
        SystemFileObject first = folderObject("/uploads/drawing-folders/RFQ/101/FOLDER/A.pdf", "A.pdf", 12L);
        SystemFileObject second = folderObject("/uploads/drawing-folders/RFQ/101/FOLDER/B.pdf", "B.pdf", 34L);
        when(objectMapper.selectList(any())).thenReturn(List.of(first, second));

        BusinessFileService service = new BusinessFileService(pathService, router, objectMapper);

        List<BusinessFileService.DrawingFolderFile> files = service.listDrawingFolderFiles(
                "/uploads/drawing-folders/RFQ/101/FOLDER/");

        assertThat(files).hasSize(2);
        assertThat(files).extracting(BusinessFileService.DrawingFolderFile::fileName)
                .containsExactly("A.pdf", "B.pdf");
        assertThat(files).extracting(BusinessFileService.DrawingFolderFile::publicPath)
                .containsExactly(first.getPublicPath(), second.getPublicPath());
        assertThat(files).extracting(BusinessFileService.DrawingFolderFile::sizeBytes)
                .containsExactly(12L, 34L);
    }

    @Test
    void businessFileServicePagesDrawingFolderFilesByFolderPrefixAndFilenameKeyword() {
        OrderFilePathService pathService = pathService();
        FileStorageRouter router = mock(FileStorageRouter.class);
        SystemFileObjectMapper objectMapper = mock(SystemFileObjectMapper.class);
        SystemFileObject file = folderObject("/uploads/drawing-folders/RFQ/101/FOLDER/A-100.pdf", "A-100.pdf", 12L);
        when(objectMapper.selectCount(any())).thenReturn(3L);
        when(objectMapper.selectList(any())).thenReturn(List.of(file));

        BusinessFileService service = new BusinessFileService(pathService, router, objectMapper);

        BusinessFileService.DrawingFolderFilePage page = service.pageDrawingFolderFiles(
                "/uploads/drawing-folders/RFQ/101/FOLDER/",
                "A-100",
                2,
                20);

        assertThat(page.records()).hasSize(1);
        assertThat(page.records()).extracting(BusinessFileService.DrawingFolderFile::fileName)
                .containsExactly("A-100.pdf");
        assertThat(page.total()).isEqualTo(3L);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.pageSize()).isEqualTo(20);
        verify(objectMapper).selectCount(any());
        verify(objectMapper).selectList(any());
    }

    @Test
    void businessFileServiceReadsMetadataFilesThroughTheirOriginalStorageProvider() throws Exception {
        OrderFilePathService pathService = pathService();
        FileStorageRouter router = mock(FileStorageRouter.class);
        SystemFileObjectMapper objectMapper = mock(SystemFileObjectMapper.class);
        SystemFileObject object = new SystemFileObject();
        object.setPublicPath("/uploads/客户A/RFQ-2026-0001/A.pdf");
        object.setStorageType("MINIO");
        object.setStorageConfigId(2L);
        object.setObjectKey("客户A/RFQ-2026-0001/A.pdf");
        object.setOriginalFilename("A.pdf");
        object.setContentType("application/pdf");
        object.setSizeBytes(3L);
        when(objectMapper.selectOne(any())).thenReturn(object);
        when(router.open(StorageType.MINIO, 2L, "客户A/RFQ-2026-0001/A.pdf"))
                .thenReturn(new InputStreamResource(new ByteArrayInputStream("pdf".getBytes(StandardCharsets.UTF_8))));

        BusinessFileService service = new BusinessFileService(pathService, router, objectMapper);
        Optional<FileReadResult> result = service.openByPublicPath("/uploads/%E5%AE%A2%E6%88%B7A/RFQ-2026-0001/A.pdf");

        assertThat(result).isPresent();
        assertThat(result.get().filename()).isEqualTo("A.pdf");
        assertThat(result.get().contentType()).isEqualTo("application/pdf");
        assertThat(result.get().contentLength()).isEqualTo(3L);
        assertThat(result.get().resource().getContentAsByteArray()).isEqualTo("pdf".getBytes(StandardCharsets.UTF_8));
        verify(router).open(StorageType.MINIO, 2L, "客户A/RFQ-2026-0001/A.pdf");
    }

    @Test
    void businessFileServiceFallsBackToLocalUploadRootForLegacyFilesWithoutMetadata() throws Exception {
        OrderFilePathService pathService = pathService();
        Path legacyFile = tempDir.resolve("客户A").resolve("RFQ-2026-0001").resolve("legacy.pdf");
        Files.createDirectories(legacyFile.getParent());
        Files.writeString(legacyFile, "legacy", StandardCharsets.UTF_8);
        SystemFileObjectMapper objectMapper = mock(SystemFileObjectMapper.class);
        when(objectMapper.selectOne(any())).thenReturn(null);

        BusinessFileService service = new BusinessFileService(pathService, mock(FileStorageRouter.class), objectMapper);
        Optional<FileReadResult> result = service.openByPublicPath("/uploads/客户A/RFQ-2026-0001/legacy.pdf");

        assertThat(result).isPresent();
        assertThat(result.get().filename()).isEqualTo("legacy.pdf");
        assertThat(result.get().resource().getContentAsByteArray()).isEqualTo("legacy".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void businessEntrypointsUseBusinessFileServiceInsteadOfDirectLocalFileWrites() throws Exception {
        String market = Files.readString(Path.of("src/main/java/com/machining/quote/controller/MarketController.java"));
        String order = Files.readString(Path.of("src/main/java/com/machining/quote/service/OrderManagementService.java"));
        String release = Files.readString(Path.of("src/main/java/com/machining/quote/service/InternalTargetLeadTimeService.java"));
        String upload = Files.readString(Path.of("src/main/java/com/machining/quote/controller/UploadFileController.java"));

        assertThat(market).contains("BusinessFileService").doesNotContain("transferTo(dest)");
        assertThat(order).contains("BusinessFileService").doesNotContain("drawing.transferTo(dest)");
        assertThat(order).doesNotContain("deleteDrawingByUrl");
        assertThat(release).contains("BusinessFileService").doesNotContain("Files.copy(inputStream, dest");
        assertThat(upload).contains("businessFileService.openByPublicPath");
    }

    private OrderFilePathService pathService() {
        OrderFilePathService service = new OrderFilePathService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        return service;
    }

    private FileReadResult readResult(String content, String contentType, String filename) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new FileReadResult(
                new InputStreamResource(new ByteArrayInputStream(bytes)),
                contentType,
                bytes.length,
                filename);
    }

    private OrderRfq order() {
        OrderRfq order = new OrderRfq();
        order.setId(101);
        order.setCustomerName("客户A");
        order.setRfqNo("RFQ-2026-0001");
        return order;
    }

    private SystemFileObject folderObject(String publicPath, String filename, Long sizeBytes) {
        SystemFileObject object = new SystemFileObject();
        object.setPublicPath(publicPath);
        object.setStorageType("MINIO");
        object.setStorageConfigId(7L);
        object.setObjectKey(publicPath.substring("/uploads/".length()));
        object.setOriginalFilename(filename);
        object.setContentType("application/pdf");
        object.setSizeBytes(sizeBytes);
        return object;
    }
}
