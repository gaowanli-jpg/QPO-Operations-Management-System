package com.machining.quote.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class StorageInitializer implements ApplicationRunner {
    @Value("${erp.upload-dir}")
    private String uploadDir;

    @Value("${erp.backup-dir}")
    private String backupDir;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        ensureDirectory(uploadDir, "上传根目录");
        ensureDirectory(backupDir, "备份目录");
    }

    private void ensureDirectory(String pathText, String label) throws IOException {
        Path path = Path.of(pathText).toAbsolutePath().normalize();
        Files.createDirectories(path);
        if (!Files.isDirectory(path) || !Files.isWritable(path)) {
            throw new IllegalStateException(label + "不可写：" + path);
        }
    }
}
