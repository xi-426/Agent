package com.yan.agent;

import com.yan.agent.document.DocumentChunkRepository;
import com.yan.agent.document.KnowledgeBaseService;
import com.yan.agent.document.KnowledgeDocument;
import com.yan.agent.document.KnowledgeDocumentRepository;
import com.yan.agent.document.KnowledgeRetrievalService;
import com.yan.agent.document.RagCalibrationCase;
import com.yan.agent.document.RagCalibrationRequest;
import com.yan.agent.document.RagCalibrationResult;
import com.yan.agent.document.RagCalibrationService;
import com.yan.agent.document.RagEvaluationSplit;
import com.yan.agent.document.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagCalibrationServiceTest {

    @Test
    void shouldChooseThresholdFromCalibrationAndOnlyReportOnTest() {
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);

        KnowledgeDocument firstDocument = mock(KnowledgeDocument.class);
        KnowledgeDocument secondDocument = mock(KnowledgeDocument.class);
        when(firstDocument.getId()).thenReturn(1L);
        when(secondDocument.getId()).thenReturn(2L);
        when(documentRepository.findByKnowledgeBaseIdOrderByIdDesc(9L))
                .thenReturn(List.of(firstDocument, secondDocument));

        when(retrievalService.retrieve(9L, "有答案校准题", 2))
                .thenReturn(List.of(chunk(11L, 1L, 0.20), chunk(12L, 2L, 0.50)));
        when(retrievalService.retrieve(9L, "无答案校准题", 2))
                .thenReturn(List.of(chunk(13L, 2L, 0.60)));
        when(retrievalService.retrieve(9L, "有答案测试题", 2))
                .thenReturn(List.of(chunk(14L, 1L, 0.30)));
        when(retrievalService.retrieve(9L, "无答案测试题", 2))
                .thenReturn(List.of(chunk(15L, 2L, 0.70)));

        RagCalibrationService service = new RagCalibrationService(
                knowledgeBaseService,
                documentRepository,
                chunkRepository,
                retrievalService);

        RagCalibrationRequest request = new RagCalibrationRequest(
                2,
                List.of(
                        evaluationCase("C1", "有答案校准题", RagEvaluationSplit.CALIBRATION, true),
                        evaluationCase("C2", "无答案校准题", RagEvaluationSplit.CALIBRATION, false),
                        evaluationCase("T1", "有答案测试题", RagEvaluationSplit.TEST, true),
                        evaluationCase("T2", "无答案测试题", RagEvaluationSplit.TEST, false)));

        RagCalibrationResult result = service.calibrate(7L, 9L, request);

        assertThat(result.selectedConfiguration().maxDistance()).isEqualTo(0.40);
        assertThat(result.calibrationMetrics().falseAcceptRate()).isZero();
        assertThat(result.testMetrics().answerRecall()).isEqualTo(1.0);
        assertThat(result.testMetrics().falseAcceptRate()).isZero();
    }

    private RagCalibrationCase evaluationCase(
            String id,
            String question,
            RagEvaluationSplit split,
            boolean expectAnswer) {
        return new RagCalibrationCase(
                id,
                question,
                split,
                expectAnswer,
                expectAnswer ? List.of(1L) : List.of(),
                List.of());
    }

    private RetrievedChunk chunk(
            Long chunkId,
            Long documentId,
            double distance) {
        return new RetrievedChunk(
                chunkId,
                documentId,
                "资料.doc",
                0,
                "评测内容",
                distance);
    }
}
