package com.machining.quote.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FileStorageTestService {
    private final FileStorageConfigService configService;
    private final FileStorageRouter router;

    @Autowired
    public FileStorageTestService(FileStorageConfigService configService, FileStorageRouter router) {
        this.configService = configService;
        this.router = router;
    }

    public StorageTestResult testActiveConfig() {
        return test(configService.activeConfig());
    }

    public StorageTestResult test(FileStorageConfig config) {
        return router.test(config);
    }
}
