package com.yan.agent.document;

public record KnowledgeBaseResponse(
        Long id,
        Long ownerId,
        String name,
        String description) {
}