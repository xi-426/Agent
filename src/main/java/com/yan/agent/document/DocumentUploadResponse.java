package com.yan.agent.document;

public class DocumentUploadResponse {

    private final Long documentId;
    private final String originalName;
    private final DocumentStatus status;

    public DocumentUploadResponse(
            Long documentId,
            String originalName,
            DocumentStatus status) {
        this.documentId = documentId;
        this.originalName = originalName;
        this.status = status;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public DocumentStatus getStatus() {
        return status;
    }
}