package com.yan.agent.document;

import java.util.List;

public record RagCalibrationResult(
        String selectionPolicy,
        RagTuningConfiguration selectedConfiguration,
        RagQualityMetrics calibrationMetrics,
        RagQualityMetrics testMetrics,
        List<RagKMetric> rawRetrievalCurve,
        List<String> warnings) {
}
