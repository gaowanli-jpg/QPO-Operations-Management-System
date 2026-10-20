package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SystemFileObject;
import com.machining.quote.mapper.SystemFileObjectMapper;
import com.machining.quote.storage.FileStorageRouter;
import com.machining.quote.storage.StorageType;
import com.machining.quote.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class BusinessFileService {
    private static final Logger log = LoggerFactory.getLogger(BusinessFileService.class);
    private static final String UPLOAD_PREFIX = "/uploads/";
    private static final String DRAWING_FOLDER_PREFIX = "/uploads/drawing-folders/";

    private final OrderFilePathService orderFilePathService;
    private final FileStorageRouter fileStorageRouter;
    private final SystemFileObjectMapper fileObjectMapper;

    @Value("${erp.public-base-url:http://localhost:8088}")
    private String publicBaseUrl;

    @Autowired
    public BusinessFileService(
            OrderFilePathService orderFilePathService,
            FileStorageRouter fileStorageRouter,
            SystemFileObjectMapper fileObjectMapper) {
        this.orderFilePathService = orderFilePathService;
        this.fileStorageRouter = fileStorageRouter;
        this.fileObjectMapper = fileObjectMapper;
    }

    public StoredBusinessFile saveDrawing(OrderRfq order, MultipartFile drawing, String bizType, String bizId) {
        if (order == null) {
            throw new IllegalArgumentException("订单不能为空");
        }
        String originalFilename = cleanFilename(drawing == null ? null : drawing.getOriginalFilename());
        String newFilename = UUID.randomUUID() + extension(originalFilename);
        String publicPath = orderFilePathService.drawingUrl(order, newFilename);
        return saveDrawingAtPublicPath(publicPath, drawing, originalFilename, bizType, bizId);
    }

    public StoredBusinessFile saveSalesOrderImportDrawing(
            String customerName,
            String batchNo,
            MultipartFile drawing,
            String bizType,
            String bizId) {
        String originalFilename = cleanFilename(drawing == null ? null : drawing.getOriginalFilename());
        String newFilename = UUID.randomUUID() + extension(originalFilename);
        String publicPath = orderFilePathService.salesOrderImportDrawingUrl(customerName, batchNo, newFilename);
        return saveDrawingAtPublicPath(publicPath, drawing, originalFilename, bizType, bizId);
    }

    public DrawingFolderSaveResult saveDrawingFolderFiles(
            String sourceType,
            String sourceId,
            String folderName,
            List<? extends MultipartFile> files,
            String bizType,
            String bizId) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("请至少上传一份文件");
        }
        String normalizedSourceType = isBlank(sourceType) ? "ORDER" : sourceType.trim().toUpperCase(Locale.ROOT);
        String normalizedSourceId = isBlank(sourceId) ? "unknown" : sourceId.trim();
        String normalizedFolderName = isBlank(folderName) ? "订单附件文件夹" : cleanFilename(folderName);
        String folderUrl = orderFilePathService.drawingFolderUrl(
                normalizedSourceType,
                normalizedSourceId,
                UUID.randomUUID().toString());
        List<DrawingFolderFile> savedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (!isSavableFile(file)) {
                continue;
            }
            String originalFilename = cleanFilename(file.getOriginalFilename());
            String publicPath = folderUrl + UUID.randomUUID() + attachmentExtension(originalFilename);
            StoredBusinessFile stored = saveDrawingAtPublicPath(publicPath, file, originalFilename, bizType, bizId);
            savedFiles.add(new DrawingFolderFile(
                    originalFilename,
                    stored.publicPath(),
                    stored.contentType(),
                    stored.sizeBytes(),
                    LocalDateTime.now()));
        }
        if (savedFiles.isEmpty()) {
            throw new IllegalArgumentException("请至少上传一份文件");
        }
        return new DrawingFolderSaveResult(folderUrl, normalizedFolderName, savedFiles.size(), List.copyOf(savedFiles));
    }

    public List<DrawingFolderFile> listDrawingFolderFiles(String rawFolderUrl) {
        String folderUrl = normalizeDrawingFolderUrl(rawFolderUrl);
        QueryWrapper<SystemFileObject> query = drawingFolderFileQuery(folderUrl, null);
        query.orderByAsc("created_at")
                .orderByAsc("id");
        List<SystemFileObject> rows = fileObjectMapper.selectList(query);
        return mapDrawingFolderFiles(rows);
    }

    public DrawingFolderFilePage pageDrawingFolderFiles(String rawFolderUrl, String keyword, int page, int pageSize) {
        String folderUrl = normalizeDrawingFolderUrl(rawFolderUrl);
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        Long totalValue = fileObjectMapper.selectCount(drawingFolderFileQuery(folderUrl, keyword));
        long total = totalValue == null ? 0L : totalValue;
        if (total <= 0) {
            return new DrawingFolderFilePage(List.of(), 0L, safePage, safePageSize);
        }
        long offset = (long) (safePage - 1) * safePageSize;
        QueryWrapper<SystemFileObject> query = drawingFolderFileQuery(folderUrl, keyword);
        query.orderByAsc("created_at")
                .orderByAsc("id")
                .last("LIMIT " + safePageSize + " OFFSET " + offset);
        List<SystemFileObject> rows = fileObjectMapper.selectList(query);
        return new DrawingFolderFilePage(mapDrawingFolderFiles(rows), total, safePage, safePageSize);
    }

    private QueryWrapper<SystemFileObject> drawingFolderFileQuery(String folderUrl, String keyword) {
        QueryWrapper<SystemFileObject> query = new QueryWrapper<>();
        query.likeRight("public_path", folderUrl);
        String normalizedKeyword = blankToNull(keyword);
        if (normalizedKeyword != null) {
            query.and(wrapper -> wrapper
                    .like("original_filename", normalizedKeyword)
                    .or()
                    .like("object_key", normalizedKeyword));
        }
        return query;
    }

    private List<DrawingFolderFile> mapDrawingFolderFiles(List<SystemFileObject> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(file -> new DrawingFolderFile(
                        filenameFromObject(file.getOriginalFilename(), file.getObjectKey()),
                        file.getPublicPath(),
                        defaultContentType(file.getContentType()),
                        file.getSizeBytes() == null ? -1L : file.getSizeBytes(),
                        file.getCreatedAt()))
                .toList();
    }

    private int normalizePage(int page) {
        return Math.max(1, page);
    }

    private int normalizePageSize(int pageSize) {
        return pageSize == 50 || pageSize == 100 ? pageSize : 20;
    }

    public boolean isDrawingFolderUrl(String value) {
        try {
            normalizeDrawingFolderUrl(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private StoredBusinessFile saveDrawingAtPublicPath(
            String publicPath,
            MultipartFile drawing,
            String originalFilename,
            String bizType,
            String bizId) {
        if (drawing == null || drawing.isEmpty()) {
            throw new IllegalArgumentException("图纸文件不能为空");
        }
        String objectKey = objectKeyFromPublicPath(publicPath);

        try (InputStream inputStream = drawing.getInputStream()) {
            StoredFile stored = fileStorageRouter.saveActive(
                    objectKey,
                    inputStream,
                    drawing.getSize(),
                    defaultContentType(drawing.getContentType()),
                    originalFilename);
            SystemFileObject fileObject = new SystemFileObject();
            fileObject.setPublicPath(publicPath);
            fileObject.setStorageType(stored.storageType().name());
            fileObject.setStorageConfigId(stored.storageConfigId());
            fileObject.setObjectKey(stored.objectKey());
            fileObject.setOriginalFilename(originalFilename);
            fileObject.setContentType(defaultContentType(drawing.getContentType()));
            fileObject.setSizeBytes(stored.sizeBytes());
            fileObject.setExternalUrl(stored.externalUrl());
            fileObject.setBizType(blankToNull(bizType));
            fileObject.setBizId(blankToNull(bizId));
            fileObject.setCreatedAt(LocalDateTime.now());
            fileObjectMapper.insert(fileObject);
            return new StoredBusinessFile(
                    publicPath,
                    stored.storageType(),
                    stored.storageConfigId(),
                    stored.objectKey(),
                    originalFilename,
                    fileObject.getContentType(),
                    stored.sizeBytes(),
                    stored.externalUrl());
        } catch (IOException e) {
            log.error("Business file save failed publicPath={} originalFilename={}", publicPathForLog(publicPath), originalFilename, e);
            throw new IllegalStateException("图纸保存失败：" + originalFilename, e);
        } catch (RuntimeException e) {
            log.error("Business file save failed publicPath={} originalFilename={}", publicPathForLog(publicPath), originalFilename, e);
            throw e;
        }
    }

    public Optional<FileReadResult> openByPublicPath(String rawPublicPath) {
        Optional<String> normalized = normalizePublicPath(rawPublicPath);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        String publicPath = normalized.get();
        SystemFileObject fileObject = findByPublicPath(publicPath);
        if (fileObject != null) {
            return Optional.of(openStoredObject(fileObject));
        }
        return openLegacyLocalFile(publicPath);
    }

    public String externalReadableUrl(String rawPublicPath) {
        if (isBlank(rawPublicPath)) {
            throw new IllegalArgumentException("Business file external URL cannot be blank");
        }
        String value = rawPublicPath.trim();
        if (isExternalUrl(value) && !externalUrlContainsUploadMapping(value)) {
            return value;
        }
        Optional<String> normalized = normalizePublicPath(value);
        if (normalized.isEmpty()) {
            if (isExternalUrl(value)) {
                return value;
            }
            throw new IllegalArgumentException("Business file external URL is unavailable");
        }
        String publicPath = normalized.get();
        SystemFileObject fileObject = findByPublicPath(publicPath);
        if (fileObject == null) {
            return toAbsolutePublicUrl(publicPath);
        }
        String storedExternalUrl = blankToNull(fileObject.getExternalUrl());
        if (storedExternalUrl != null) {
            return storedExternalUrl;
        }
        StorageType storageType = StorageType.fromCode(fileObject.getStorageType());
        Optional<String> generatedUrl = fileStorageRouter.externalReadUrl(
                storageType,
                fileObject.getStorageConfigId(),
                fileObject.getObjectKey());
        if (generatedUrl.isPresent()) {
            return generatedUrl.get();
        }
        if (storageType == StorageType.LOCAL) {
            return toAbsolutePublicUrl(publicPath);
        }
        throw new IllegalStateException("Business file external URL is unavailable publicPath=" + publicPath);
    }

    public boolean deleteByPublicPath(String rawPublicPath) {
        Optional<String> normalized = normalizePublicPath(rawPublicPath);
        if (normalized.isEmpty()) {
            return false;
        }
        String publicPath = normalized.get();
        SystemFileObject fileObject = findByPublicPath(publicPath);
        if (fileObject != null) {
            try {
                StorageType storageType = StorageType.fromCode(fileObject.getStorageType());
                fileStorageRouter.delete(storageType, fileObject.getStorageConfigId(), fileObject.getObjectKey());
                deleteMetadata(publicPath);
                return true;
            } catch (RuntimeException e) {
                log.warn("Business file delete skipped publicPath={} reason={}", publicPath, e.getMessage());
                return false;
            }
        }
        return deleteLegacyLocalFile(publicPath);
    }

    private FileReadResult openStoredObject(SystemFileObject fileObject) {
        StorageType storageType = StorageType.fromCode(fileObject.getStorageType());
        try {
            InputStreamResource resource = fileStorageRouter.open(
                    storageType,
                    fileObject.getStorageConfigId(),
                    fileObject.getObjectKey());
            return new FileReadResult(
                    resource,
                    defaultContentType(fileObject.getContentType()),
                    fileObject.getSizeBytes() == null ? -1L : fileObject.getSizeBytes(),
                    filenameFromObject(fileObject.getOriginalFilename(), fileObject.getObjectKey()));
        } catch (RuntimeException e) {
            log.error("Business file read failed publicPath={} storageType={} objectKey={}",
                    publicPathForLog(fileObject.getPublicPath()), fileObject.getStorageType(), fileObject.getObjectKey(), e);
            throw e;
        }
    }

    private Optional<FileReadResult> openLegacyLocalFile(String publicPath) {
        Path file = legacyLocalPath(publicPath);
        if (file == null || !Files.isRegularFile(file)) {
            log.warn("Business file missing publicPath={} resolvedPath={}", publicPath, file);
            return Optional.empty();
        }
        try {
            String contentType = Files.probeContentType(file);
            return Optional.of(new FileReadResult(
                    new InputStreamResource(Files.newInputStream(file)),
                    defaultContentType(contentType),
                    Files.size(file),
                    file.getFileName().toString()));
        } catch (IOException e) {
            log.error("Business file read failed publicPath={} resolvedPath={}", publicPath, file, e);
            throw new IllegalStateException("图纸读取失败：" + publicPath, e);
        }
    }

    private boolean deleteLegacyLocalFile(String publicPath) {
        Path file = legacyLocalPath(publicPath);
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        try {
            Files.deleteIfExists(file);
            return true;
        } catch (IOException e) {
            log.warn("Business file delete skipped publicPath={} resolvedPath={} reason={}",
                    publicPath, file, e.getMessage());
            return false;
        }
    }

    private SystemFileObject findByPublicPath(String publicPath) {
        QueryWrapper<SystemFileObject> query = new QueryWrapper<>();
        query.eq("public_path", publicPath).last("LIMIT 1");
        return fileObjectMapper.selectOne(query);
    }

    private void deleteMetadata(String publicPath) {
        UpdateWrapper<SystemFileObject> wrapper = new UpdateWrapper<>();
        wrapper.eq("public_path", publicPath);
        fileObjectMapper.delete(wrapper);
    }

    private String toAbsolutePublicUrl(String publicPath) {
        if (isExternalUrl(publicPath)) {
            return publicPath;
        }
        String base = isBlank(publicBaseUrl) ? "http://localhost:8088" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + (publicPath.startsWith("/") ? publicPath : "/" + publicPath);
    }

    private boolean isExternalUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private boolean externalUrlContainsUploadMapping(String value) {
        try {
            String path = URI.create(value).getPath();
            return path != null && path.contains(UPLOAD_PREFIX);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Optional<String> normalizePublicPath(String rawPublicPath) {
        if (rawPublicPath == null || rawPublicPath.trim().isEmpty()) {
            log.warn("Business file access rejected publicPath={} reason={}", rawPublicPath, "blank_public_path");
            return Optional.empty();
        }
        String path = rawPublicPath.trim();
        try {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                path = URI.create(path).getPath();
            }
        } catch (IllegalArgumentException e) {
            log.warn("Business file access rejected publicPath={} reason={}", rawPublicPath, "invalid_uri");
            return Optional.empty();
        }
        int uploadIndex = path.indexOf(UPLOAD_PREFIX);
        if (uploadIndex < 0) {
            log.warn("Business file access rejected publicPath={} reason={}", rawPublicPath, "outside_upload_mapping");
            return Optional.empty();
        }
        path = path.substring(uploadIndex);
        path = UriUtils.decode(path, StandardCharsets.UTF_8);
        try {
            objectKeyFromPublicPath(path);
            return Optional.of(path);
        } catch (IllegalArgumentException e) {
            log.warn("Business file access rejected publicPath={} reason={}", rawPublicPath, e.getMessage());
            return Optional.empty();
        }
    }

    public String normalizeDrawingFolderUrl(String rawFolderUrl) {
        Optional<String> normalized = normalizePublicPath(rawFolderUrl);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("附件文件夹地址不正确");
        }
        String folderUrl = normalized.get();
        if (!folderUrl.startsWith(DRAWING_FOLDER_PREFIX) || !folderUrl.endsWith("/")) {
            throw new IllegalArgumentException("附件文件夹地址不正确");
        }
        return folderUrl;
    }

    private Path legacyLocalPath(String publicPath) {
        String objectKey = objectKeyFromPublicPath(publicPath);
        Path root = orderFilePathService.uploadRoot().toAbsolutePath().normalize();
        Path file = root.resolve(objectKey).toAbsolutePath().normalize();
        if (!file.startsWith(root)) {
            log.warn("Business file access rejected publicPath={} reason={}", publicPath, "outside_upload_root");
            return null;
        }
        return file;
    }

    private String objectKeyFromPublicPath(String publicPath) {
        if (publicPath == null || !publicPath.startsWith(UPLOAD_PREFIX)) {
            throw new IllegalArgumentException("outside_upload_mapping");
        }
        String objectKey = publicPath.substring(UPLOAD_PREFIX.length()).replace('\\', '/');
        if (objectKey.isBlank()) {
            throw new IllegalArgumentException("blank_object_key");
        }
        Path relative = Path.of(objectKey).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")
                || objectKey.startsWith("/") || objectKey.contains("../") || objectKey.contains("/..")) {
            throw new IllegalArgumentException("outside_upload_root");
        }
        return objectKey;
    }

    private String cleanFilename(String originalFilename) {
        if (originalFilename == null) {
            return "drawing.pdf";
        }
        String cleaned = originalFilename.replaceAll(".*[\\\\/]", "").trim();
        return cleaned.isBlank() ? "drawing.pdf" : cleaned;
    }

    private boolean isSavableFile(MultipartFile file) {
        return file != null && !file.isEmpty() && file.getOriginalFilename() != null;
    }

    private String extension(String filename) {
        return extension(filename, ".pdf");
    }

    private String attachmentExtension(String filename) {
        return extension(filename, "");
    }

    private String extension(String filename, String defaultExtension) {
        int dotIndex = filename == null ? -1 : filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return defaultExtension;
        }
        String ext = filename.substring(dotIndex).toLowerCase();
        return ext.length() > 20 ? defaultExtension : ext;
    }

    private String filenameFromObject(String originalFilename, String objectKey) {
        if (originalFilename != null && !originalFilename.isBlank()) {
            return originalFilename;
        }
        int slashIndex = objectKey == null ? -1 : objectKey.lastIndexOf('/');
        return slashIndex < 0 ? objectKey : objectKey.substring(slashIndex + 1);
    }

    private String defaultContentType(String contentType) {
        return contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : contentType;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String publicPathForLog(String publicPath) {
        if (isBlank(publicPath)) {
            return "";
        }
        if (publicPath.startsWith(DRAWING_FOLDER_PREFIX)) {
            return DRAWING_FOLDER_PREFIX + "[redacted]";
        }
        return publicPath;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record StoredBusinessFile(
            String publicPath,
            StorageType storageType,
            Long storageConfigId,
            String objectKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String externalUrl) {
    }

    public record FileReadResult(
            InputStreamResource resource,
            String contentType,
            long contentLength,
            String filename) {
    }

    public record DrawingFolderSaveResult(
            String folderUrl,
            String folderName,
            int fileCount,
            List<DrawingFolderFile> files) {
    }

    public record DrawingFolderFile(
            String fileName,
            String publicPath,
            String contentType,
            long sizeBytes,
            LocalDateTime createdAt) {
    }

    public record DrawingFolderFilePage(
            List<DrawingFolderFile> records,
            long total,
            int page,
            int pageSize) {
    }
}
