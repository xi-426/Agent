package com.yan.agent.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RetrievalEvaluationService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeRetrievalService retrievalService;
    private final HybridRerankerService rerankerService;
    private final int topK;
    private final int candidateK;
    private final double maxDistance;

    public RetrievalEvaluationService(
            KnowledgeBaseService knowledgeBaseService,
            KnowledgeRetrievalService retrievalService,
            HybridRerankerService rerankerService,
            @Value("${app.rag.top-k}") int topK,
            @Value("${app.rag.candidate-k}") int candidateK,
            @Value("${app.rag.max-distance}") double maxDistance) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.retrievalService = retrievalService;
        this.rerankerService = rerankerService;
        this.topK = topK;
        this.candidateK = candidateK;
        this.maxDistance = maxDistance;
    }

    public RetrievalEvaluationResult evaluate(
            Long ownerId,
            Long knowledgeBaseId,
            List<RetrievalEvaluationCase> cases) {

        // 评测前仍然必须检查知识库所有权
        knowledgeBaseService.requireOwnedBy(
                knowledgeBaseId,
                ownerId);

        List<RetrievalEvaluationItemResult> itemResults = new ArrayList<>();

        int sourcedCases = 0;
        int hitCases = 0;
        int correctDecisions = 0;

        for (RetrievalEvaluationCase evaluationCase : cases) {

            List<RetrievedChunk> candidates = retrievalService.retrieve(
                    knowledgeBaseId,
                    evaluationCase.question(),
                    candidateK);

            Double nearestDistance = candidates.isEmpty()
                    ? null
                    : candidates.get(0).getDistance();

            List<RetrievedChunk> acceptedCandidates = new ArrayList<>();
            List<String> retrievedDocuments = new ArrayList<>();

            boolean hasExpectedDocument = evaluationCase.expectedDocumentName() != null
                    && !evaluationCase.expectedDocumentName().isBlank();

            Boolean hitAtK = hasExpectedDocument
                    ? false
                    : null;

            if (hasExpectedDocument) {
                sourcedCases++;
            }

            for (RetrievedChunk candidate : candidates) {
                if (candidate.getDistance() <= maxDistance) {
                    acceptedCandidates.add(candidate);
                }
            }

            boolean wouldAnswer = !acceptedCandidates.isEmpty();

            List<RetrievedChunk> finalChunks = rerankerService.rerank(
                    evaluationCase.question(),
                    acceptedCandidates,
                    topK);

            for (RetrievedChunk candidate : finalChunks) {
                retrievedDocuments.add(candidate.getDocumentName());

                if (hasExpectedDocument
                        && evaluationCase
                                .expectedDocumentName()
                                .equals(candidate.getDocumentName())) {
                    hitAtK = true;
                }
            }

            if (Boolean.TRUE.equals(hitAtK)) {
                hitCases++;
            }

            boolean decisionCorrect = evaluationCase.expectAnswer() == wouldAnswer;

            if (decisionCorrect) {
                correctDecisions++;
            }

            RetrievalEvaluationItemResult itemResult = new RetrievalEvaluationItemResult(
                    evaluationCase.id(),
                    evaluationCase.question(),
                    evaluationCase.expectedDocumentName(),
                    evaluationCase.expectAnswer(),
                    nearestDistance,
                    wouldAnswer,
                    hitAtK,
                    decisionCorrect,
                    retrievedDocuments);

            itemResults.add(itemResult);
        }

        double hitAtKRate = sourcedCases == 0
                ? 0.0
                : (double) hitCases / sourcedCases;

        double decisionAccuracy = cases.isEmpty()
                ? 0.0
                : (double) correctDecisions / cases.size();

        return new RetrievalEvaluationResult(
                topK,
                candidateK,
                maxDistance,
                cases.size(),
                sourcedCases,
                hitCases,
                hitAtKRate,
                correctDecisions,
                decisionAccuracy,
                itemResults);
    }
}
