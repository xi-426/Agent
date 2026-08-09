package com.yan.agent.document;

public record DocumentResponse(
        Long id,
        String originalName,
        String contentType,
        long sizeBytes,
        DocumentStatus status) {
}
