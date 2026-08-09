package com.yan.agent.document;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class DocumentEmbeddingService {

    private static final int EXPECTED_DIMENSIONS = 1024;

    private final EmbeddingModel embeddingModel;

    public DocumentEmbeddingService(
            EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "需要向量化的文字不能为空");
        }

        float[] embedding = embeddingModel.embed(text);

        if (embedding.length != EXPECTED_DIMENSIONS) {
            throw new IllegalStateException(
                    "向量维度不正确，实际维度："
                            + embedding.length);
        }

        return embedding;
    }
}
