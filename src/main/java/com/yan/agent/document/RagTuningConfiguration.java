package com.yan.agent.document;

public record RagTuningConfiguration(
        int topK,
        double maxDistance) {
}
