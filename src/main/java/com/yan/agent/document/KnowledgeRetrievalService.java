package com.yan.agent.document;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeRetrievalService {

    private final DocumentEmbeddingService embeddingService;
    private final DocumentVectorRepository vectorRepository;

    public KnowledgeRetrievalService(
            DocumentEmbeddingService embeddingService,
            DocumentVectorRepository vectorRepository) {
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
    }

    public List<RetrievedChunk> retrieve(
            Long knowledgeBaseId,
            String question,
            int topK) {
        if (question == null
                || question.isBlank()) {
            throw new IllegalArgumentException(
                    "问题不能为空");
        }

        float[] queryEmbedding = embeddingService.embed(question);

        return vectorRepository.searchSimilar(
                knowledgeBaseId,
                queryEmbedding,
                topK);
    }
}
