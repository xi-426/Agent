package com.yan.agent.document;

import java.util.List;

/*hitAtKRate = 命中正确文档的题数 / 有期望文档的题数

decisionAccuracy = 回答/拒答决策正确题数 / 全部题数
 */
public record RetrievalEvaluationResult(
        int topK,
        int candidateK,
        double maxDistance,
        int totalCases,
        int sourcedCases,
        int hitCases,
        double hitAtKRate,
        int correctDecisions,
        double decisionAccuracy,
        List<RetrievalEvaluationItemResult> items) {
}
