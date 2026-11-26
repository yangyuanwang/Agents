package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties(prefix="app.file")
public class FileStorageProperties {
    private Path storageRoot = Path.of("data", "files");
    private long maxSizeBytes = 20L * 1024 * 1024;
    private List<String> allowedExtensions = List.of("pdf", "docx", "txt", "md");
    private long cleanupRetryIntervalMs = 300000;
    private int cleanupBatchSize = 100;
    public Path getStorageRoot(){return storageRoot;} public void setStorageRoot(Path v){storageRoot=v;}
    public long getMaxSizeBytes(){return maxSizeBytes;} public void setMaxSizeBytes(long v){maxSizeBytes=v;}
    public List<String> getAllowedExtensions(){return allowedExtensions;} public void setAllowedExtensions(List<String> v){allowedExtensions=v;}
    public long getCleanupRetryIntervalMs(){return cleanupRetryIntervalMs;} public void setCleanupRetryIntervalMs(long v){cleanupRetryIntervalMs=v;}
    public int getCleanupBatchSize(){return cleanupBatchSize;} public void setCleanupBatchSize(int v){cleanupBatchSize=v;}
}
