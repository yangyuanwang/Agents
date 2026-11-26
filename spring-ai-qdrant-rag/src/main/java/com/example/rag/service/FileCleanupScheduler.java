package com.example.rag.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FileCleanupScheduler {
    private final FileObjectService service;
    public FileCleanupScheduler(FileObjectService service){this.service=service;}
    @Scheduled(fixedDelayString="${app.file.cleanup.retry-interval-ms:300000}")
    public void retryDeletingFiles(){service.retryCleanupBatch();}
}
