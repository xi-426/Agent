package com.yan.agent.document;

public record RagQualityMetrics(
        String split,
        int totalCases,
        int answerableCases,
        int unanswerableCases,
        double hitAtK,
        double recallAtK,
        double mrrAtK,
        double ndcgAtK,
        double contextPrecisionAtK,
        int truePositive,
        int falsePositive,
        int trueNegative,
        int falseNegative,
        double decisionAccuracy,
        double answerPrecision,
        double answerRecall,
        double falseAcceptRate,
        double falseRejectRate) {
}
