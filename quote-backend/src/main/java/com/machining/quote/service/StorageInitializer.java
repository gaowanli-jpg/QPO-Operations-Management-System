package com.machining.quote.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class StorageInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StorageInitializer.class);

    @Value("${erp.upload-dir}")
    private String uploadDir;

    @Value("${erp.backup-dir}")
    private String backupDir;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        try {
            ensureDirectory(uploadDir, "上传根目录");
            ensureDirectory(backupDir, "备份目录");
            log.info("Storage directories initialized uploadRoot={} backupRoot={}",
                    normalizedPath(uploadDir), normalizedPath(backupDir));
        } catch (IOException | RuntimeException e) {
            log.error("Storage directory initialization failed uploadRoot={} backupRoot={}",
                    safePath(uploadDir), safePath(backupDir), e);
            throw e;
        }
    }

    private void ensureDirectory(String pathText, String label) throws IOException {
        Path path = normalizedPath(pathText);
        Files.createDirectories(path);
        if (!Files.isDirectory(path) || !Files.isWritable(path)) {
            throw new IllegalStateException(label + "不可写：" + path);
        }
    }

    private Path normalizedPath(String pathText) {
        return Path.of(pathText).toAbsolutePath().normalize();
    }

    private String safePath(String pathText) {
        try {
            return normalizedPath(pathText).toString();
        } catch (RuntimeException e) {
            return "<invalid>";
        }
    }
}
