package com.yan.agent.document;

import java.util.List;

public record RetrievalEvaluationItemResult(
        String id,
        String question,
        String expectedDocumentName,
        boolean expectAnswer,
        Double nearestDistance,
        boolean wouldAnswer,
        Boolean hitAtK,
        boolean decisionCorrect,
        List<String> retrievedDocuments) {
}