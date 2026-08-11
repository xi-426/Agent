package com.yan.agent.document;

public record BatchDocumentUploadItemResponse(
        String originalName,
        boolean success,
        Long documentId,
        DocumentStatus status,
        String message) {

    public static BatchDocumentUploadItemResponse success(
            KnowledgeDocument document) {
        return new BatchDocumentUploadItemResponse(
                document.getOriginalName(),
                true,
                document.getId(),
                document.getStatus(),
                "文档处理完成");
    }

    public static BatchDocumentUploadItemResponse failure(
            String originalName,
            String message) {
        return new BatchDocumentUploadItemResponse(
                originalName,
                false,
                null,
                null,
                message);
    }
}
