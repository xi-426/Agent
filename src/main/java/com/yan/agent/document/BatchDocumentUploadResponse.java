package com.yan.agent.document;

import java.util.List;

public record BatchDocumentUploadResponse(
        int totalCount,
        int successCount,
        int failureCount,
        List<BatchDocumentUploadItemResponse> items) {

    public BatchDocumentUploadResponse {
        items = List.copyOf(items);
    }
}
