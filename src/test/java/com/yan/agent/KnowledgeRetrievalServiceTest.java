package com.yan.agent;

import com.yan.agent.document.KnowledgeRetrievalService;
import com.yan.agent.document.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KnowledgeRetrievalServiceTest {

    @Autowired
    private KnowledgeRetrievalService retrievalService;

    @Test
    void shouldRetrieveTopChunksInDistanceOrder() {
        List<RetrievedChunk> results = retrievalService.retrieve(
                1L,
                "客户数据应该如何保护？",
                3);

        assertThat(results)
                .hasSize(3);

        assertThat(
                results.get(0).getDistance())
                .isLessThanOrEqualTo(
                        results.get(1).getDistance());
    }
}
