package com.yan.agent.document;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.document")
public class DocumentProperties {

    private String storageRoot;
    private long maxSizeBytes;
    private int maxBatchFiles;
    private long maxBatchTotalSizeBytes;
    private int chunkSize;
    private int chunkOverlap;

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public int getMaxBatchFiles() {
        return maxBatchFiles;
    }

    public void setMaxBatchFiles(int maxBatchFiles) {
        this.maxBatchFiles = maxBatchFiles;
    }

    public long getMaxBatchTotalSizeBytes() {
        return maxBatchTotalSizeBytes;
    }

    public void setMaxBatchTotalSizeBytes(long maxBatchTotalSizeBytes) {
        this.maxBatchTotalSizeBytes = maxBatchTotalSizeBytes;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }
}
