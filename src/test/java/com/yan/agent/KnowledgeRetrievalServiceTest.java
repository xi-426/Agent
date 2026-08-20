package com.yan.agent;

import com.yan.agent.document.KnowledgeRetrievalService;
import com.yan.agent.document.DocumentEmbeddingService;
import com.yan.agent.document.DocumentVectorRepository;
import com.yan.agent.document.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeRetrievalServiceTest {

    @Test
    void shouldEmbedQuestionAndReturnRepositoryResults() {
        DocumentEmbeddingService embeddingService =
                mock(DocumentEmbeddingService.class);
        DocumentVectorRepository vectorRepository =
                mock(DocumentVectorRepository.class);
        KnowledgeRetrievalService retrievalService =
                new KnowledgeRetrievalService(
                        embeddingService,
                        vectorRepository);

        float[] queryEmbedding = new float[]{0.1F, 0.2F};
        List<RetrievedChunk> expected = List.of(
                new RetrievedChunk(
                        1L,
                        10L,
                        "通知.doc",
                        0,
                        "测试内容",
                        0.21));
        when(embeddingService.embed("国家奖学金需要什么材料？"))
                .thenReturn(queryEmbedding);
        when(vectorRepository.searchSimilar(21L, queryEmbedding, 3))
                .thenReturn(expected);

        List<RetrievedChunk> actual = retrievalService.retrieve(
                21L,
                "国家奖学金需要什么材料？",
                3);

        assertThat(actual).isSameAs(expected);
        verify(embeddingService).embed("国家奖学金需要什么材料？");
        verify(vectorRepository).searchSimilar(21L, queryEmbedding, 3);
    }
}
