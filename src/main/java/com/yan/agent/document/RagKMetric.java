package com.yan.agent.document;

public record RagKMetric(
        int k,
        double hitAtK,
        double recallAtK,
        double mrrAtK,
        double ndcgAtK) {
}
