package com.yan.agent;

import com.yan.agent.document.DocumentEmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DocumentEmbeddingServiceTest {

    @Autowired
    private DocumentEmbeddingService embeddingService;

    @Test
    void shouldCreate1024DimensionalEmbedding() {
        float[] embedding = embeddingService.embed(
                "客户数据必须加密");

        assertThat(embedding)
                .hasSize(1024);
    }
}
