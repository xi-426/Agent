package com.yan.agent;

import com.yan.agent.chat.AiChatService;
import com.yan.agent.document.KnowledgeRetrievalService;
import com.yan.agent.document.KnowledgeBaseService;
import com.yan.agent.document.HybridRerankerService;
import com.yan.agent.document.RagResult;
import com.yan.agent.document.RagService;
import com.yan.agent.document.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private KnowledgeRetrievalService retrievalService;

    @Mock
    private AiChatService aiChatService;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    private HybridRerankerService rerankerService;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        rerankerService = new HybridRerankerService();
        ragService = new RagService(
                retrievalService,
                knowledgeBaseService,
                aiChatService,
                rerankerService,
                3,
                6,
                0.65);
    }

    @Test
    void shouldAnswerWithAcceptedKnowledgeChunk() {
        String question = "如何保护客户数据？";

        RetrievedChunk acceptedChunk = new RetrievedChunk(
                11L,
                21L,
                "数据安全规范.md",
                0,
                "客户数据必须加密保存。",
                0.40);

        when(retrievalService.retrieve(
                1L,
                question,
                6))
                .thenReturn(List.of(acceptedChunk));

        when(aiChatService.chat(anyString()))
                .thenReturn("客户数据必须加密保存。[资料1]");

        RagResult result = ragService.answer(
                7L,
                1L,
                question);

        assertThat(result.getAnswer())
                .isEqualTo("客户数据必须加密保存。[资料1]");

        assertThat(result.getSources())
                .containsExactly(acceptedChunk);

        //verify(...)：检查模型确实被调用。
        verify(aiChatService).chat(anyString());
    }

    @Test
    void shouldRejectWithoutCallingModelWhenKnowledgeIsTooFar() {
        String question = "火星上如何种土豆？";

        RetrievedChunk farChunk = new RetrievedChunk(
                12L,
                22L,
                "公司规范.md",
                0,
                "公司应当保护客户数据。",
                0.80);

        when(retrievalService.retrieve(
                1L,
                question,
                6))
                .thenReturn(List.of(farChunk));

        RagResult result = ragService.answer(
                7L,
                1L,
                question);

        assertThat(result.getAnswer())
                .isEqualTo("知识库中没有足够资料回答该问题。");

        assertThat(result.getSources())
                .isEmpty();

        //verify(..., never())：检查拒答时绝对没有调用模型，避免浪费 API 费用。
        verify(aiChatService, never())
                .chat(anyString());
    }
}
