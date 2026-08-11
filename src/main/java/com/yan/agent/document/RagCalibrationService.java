package com.yan.agent.document;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RagCalibrationService {

    private static final String POLICY =
            "校准集零误答优先；在此约束下最大化回答召回和检索质量，再选择更小的上下文配置";

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final KnowledgeRetrievalService retrievalService;

    public RagCalibrationService(
            KnowledgeBaseService knowledgeBaseService,
            KnowledgeDocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            KnowledgeRetrievalService retrievalService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.retrievalService = retrievalService;
    }

    public RagCalibrationResult calibrate(
            Long ownerId,
            Long knowledgeBaseId,
            RagCalibrationRequest request) {
        knowledgeBaseService.requireOwnedBy(knowledgeBaseId, ownerId);
        validateCases(knowledgeBaseId, request.cases());

        List<EvaluatedCase> evaluatedCases = new ArrayList<>();
        for (RagCalibrationCase evaluationCase : request.cases()) {
            List<RetrievedChunk> candidates = retrievalService.retrieve(
                    knowledgeBaseId,
                    evaluationCase.question(),
                    request.maxCandidateK());
            evaluatedCases.add(new EvaluatedCase(evaluationCase, candidates));
        }

        List<EvaluatedCase> calibrationCases = bySplit(
                evaluatedCases,
                RagEvaluationSplit.CALIBRATION);
        List<EvaluatedCase> testCases = bySplit(
                evaluatedCases,
                RagEvaluationSplit.TEST);

        double threshold = chooseThreshold(calibrationCases);
        RagTuningConfiguration selected = chooseTopK(
                calibrationCases,
                request.maxCandidateK(),
                threshold);

        RagQualityMetrics calibrationMetrics = evaluate(
                "CALIBRATION",
                calibrationCases,
                selected);
        RagQualityMetrics testMetrics = testCases.isEmpty()
                ? null
                : evaluate("TEST", testCases, selected);

        List<RagKMetric> rawCurve = buildRawRetrievalCurve(
                calibrationCases,
                request.maxCandidateK());
        List<String> warnings = buildWarnings(
                calibrationMetrics,
                testMetrics,
                testCases.isEmpty());

        return new RagCalibrationResult(
                POLICY,
                selected,
                calibrationMetrics,
                testMetrics,
                rawCurve,
                warnings);
    }

    private void validateCases(
            Long knowledgeBaseId,
            List<RagCalibrationCase> cases) {
        Set<String> ids = new HashSet<>();
        int calibrationAnswerable = 0;
        int calibrationUnanswerable = 0;

        Set<Long> knowledgeDocumentIds = new HashSet<>();
        for (KnowledgeDocument document : documentRepository
                .findByKnowledgeBaseIdOrderByIdDesc(knowledgeBaseId)) {
            knowledgeDocumentIds.add(document.getId());
        }

        Set<Long> labelledChunkIds = new HashSet<>();
        for (RagCalibrationCase evaluationCase : cases) {
            if (!ids.add(evaluationCase.id())) {
                throw new IllegalArgumentException(
                        "评测编号不能重复：" + evaluationCase.id());
            }
            boolean hasLabels = !evaluationCase.relevantChunkIds().isEmpty()
                    || !evaluationCase.relevantDocumentIds().isEmpty();
            if (evaluationCase.expectAnswer() && !hasLabels) {
                throw new IllegalArgumentException(
                        "有答案用例必须标注相关文档或切片：" + evaluationCase.id());
            }
            if (!evaluationCase.expectAnswer() && hasLabels) {
                throw new IllegalArgumentException(
                        "无答案用例不能标注相关来源：" + evaluationCase.id());
            }
            if (!knowledgeDocumentIds.containsAll(evaluationCase.relevantDocumentIds())) {
                throw new IllegalArgumentException(
                        "用例包含不属于当前知识库的文档ID：" + evaluationCase.id());
            }
            labelledChunkIds.addAll(evaluationCase.relevantChunkIds());

            if (evaluationCase.split() == RagEvaluationSplit.CALIBRATION) {
                if (evaluationCase.expectAnswer()) {
                    calibrationAnswerable++;
                } else {
                    calibrationUnanswerable++;
                }
            }
        }

        List<DocumentChunk> labelledChunks = chunkRepository.findAllById(labelledChunkIds);
        if (labelledChunks.size() != labelledChunkIds.size()
                || labelledChunks.stream()
                .anyMatch(chunk -> !knowledgeDocumentIds.contains(chunk.getDocumentId()))) {
            throw new IllegalArgumentException(
                    "相关切片ID必须真实存在且属于当前知识库");
        }
        if (calibrationAnswerable == 0 || calibrationUnanswerable == 0) {
            throw new IllegalArgumentException(
                    "校准集必须同时包含有答案和无答案用例");
        }
    }

    private List<EvaluatedCase> bySplit(
            List<EvaluatedCase> cases,
            RagEvaluationSplit split) {
        return cases.stream()
                .filter(item -> item.evaluationCase().split() == split)
                .toList();
    }

    private double chooseThreshold(List<EvaluatedCase> calibrationCases) {
        List<Double> observedDistances = calibrationCases.stream()
                .map(this::nearestDistance)
                .filter(Double::isFinite)
                .distinct()
                .sorted()
                .toList();

        if (observedDistances.isEmpty()) {
            return 0.0;
        }

        List<Double> candidates = new ArrayList<>();
        candidates.add(Math.max(0.0, Math.nextDown(observedDistances.get(0))));
        candidates.addAll(observedDistances);

        ThresholdScore best = null;
        for (double threshold : candidates) {
            ThresholdScore score = scoreThreshold(calibrationCases, threshold);
            if (score.falsePositive() == 0
                    && (best == null
                    || score.truePositive() > best.truePositive()
                    || (score.truePositive() == best.truePositive()
                    && score.threshold() < best.threshold()))) {
                best = score;
            }
        }

        if (best == null) {
            return 0.0;
        }

        int bestIndex = observedDistances.indexOf(best.threshold());
        if (bestIndex >= 0 && bestIndex + 1 < observedDistances.size()) {
            double nextBoundary = observedDistances.get(bestIndex + 1);
            return best.threshold() + (nextBoundary - best.threshold()) / 2.0;
        }
        return best.threshold();
    }

    private ThresholdScore scoreThreshold(
            List<EvaluatedCase> cases,
            double threshold) {
        int truePositive = 0;
        int falsePositive = 0;
        for (EvaluatedCase item : cases) {
            boolean wouldAnswer = nearestDistance(item) <= threshold;
            if (wouldAnswer && item.evaluationCase().expectAnswer()) {
                truePositive++;
            } else if (wouldAnswer) {
                falsePositive++;
            }
        }
        return new ThresholdScore(threshold, truePositive, falsePositive);
    }

    private double nearestDistance(EvaluatedCase item) {
        return item.candidates().isEmpty()
                ? Double.POSITIVE_INFINITY
                : item.candidates().get(0).getDistance();
    }

    private RagTuningConfiguration chooseTopK(
            List<EvaluatedCase> calibrationCases,
            int maxCandidateK,
            double threshold) {
        ConfigurationScore best = null;

        for (int topK = 1; topK <= maxCandidateK; topK++) {
            RagTuningConfiguration configuration =
                    new RagTuningConfiguration(topK, threshold);
            RagQualityMetrics metrics = evaluate(
                    "CALIBRATION",
                    calibrationCases,
                    configuration);
            ConfigurationScore score = new ConfigurationScore(
                    configuration,
                    metrics);
            if (best == null || isBetter(score, best)) {
                best = score;
            }
        }

        return best.configuration();
    }

    private boolean isBetter(
            ConfigurationScore candidate,
            ConfigurationScore current) {
        Comparator<ConfigurationScore> comparator = Comparator
                .comparingDouble((ConfigurationScore score) -> score.metrics().hitAtK())
                .thenComparingDouble(score -> score.metrics().recallAtK())
                .thenComparingDouble(score -> score.metrics().mrrAtK())
                .thenComparingDouble(score -> score.metrics().ndcgAtK())
                .thenComparingDouble(score -> score.metrics().contextPrecisionAtK())
                .thenComparingInt(score -> -score.configuration().topK());
        return comparator.compare(candidate, current) > 0;
    }

    private RagQualityMetrics evaluate(
            String split,
            List<EvaluatedCase> cases,
            RagTuningConfiguration configuration) {
        int answerable = 0;
        int unanswerable = 0;
        int hits = 0;
        double recallSum = 0.0;
        double reciprocalRankSum = 0.0;
        double ndcgSum = 0.0;
        double precisionSum = 0.0;
        int truePositive = 0;
        int falsePositive = 0;
        int trueNegative = 0;
        int falseNegative = 0;

        for (EvaluatedCase item : cases) {
            List<RetrievedChunk> accepted = item.candidates().stream()
                    .limit(configuration.topK())
                    .filter(chunk -> chunk.getDistance() <= configuration.maxDistance())
                    .toList();
            boolean wouldAnswer = !accepted.isEmpty();

            if (item.evaluationCase().expectAnswer()) {
                answerable++;
                if (wouldAnswer) {
                    truePositive++;
                } else {
                    falseNegative++;
                }
            } else {
                unanswerable++;
                if (wouldAnswer) {
                    falsePositive++;
                } else {
                    trueNegative++;
                }
            }

            if (!item.evaluationCase().expectAnswer()) {
                continue;
            }

            RankingScore ranking = scoreRanking(
                    item.evaluationCase(),
                    accepted,
                    configuration.topK());
            if (ranking.hit()) {
                hits++;
            }
            recallSum += ranking.recall();
            reciprocalRankSum += ranking.reciprocalRank();
            ndcgSum += ranking.ndcg();
            precisionSum += ranking.precision();
        }

        return new RagQualityMetrics(
                split,
                cases.size(),
                answerable,
                unanswerable,
                divide(hits, answerable),
                divide(recallSum, answerable),
                divide(reciprocalRankSum, answerable),
                divide(ndcgSum, answerable),
                divide(precisionSum, answerable),
                truePositive,
                falsePositive,
                trueNegative,
                falseNegative,
                divide(truePositive + trueNegative, cases.size()),
                divide(truePositive, truePositive + falsePositive),
                divide(truePositive, truePositive + falseNegative),
                divide(falsePositive, unanswerable),
                divide(falseNegative, answerable));
    }

    private List<RagKMetric> buildRawRetrievalCurve(
            List<EvaluatedCase> calibrationCases,
            int maxCandidateK) {
        List<RagKMetric> curve = new ArrayList<>();
        List<EvaluatedCase> answerableCases = calibrationCases.stream()
                .filter(item -> item.evaluationCase().expectAnswer())
                .toList();

        for (int k = 1; k <= maxCandidateK; k++) {
            int hits = 0;
            double recall = 0.0;
            double mrr = 0.0;
            double ndcg = 0.0;
            for (EvaluatedCase item : answerableCases) {
                RankingScore score = scoreRanking(
                        item.evaluationCase(),
                        item.candidates().stream().limit(k).toList(),
                        k);
                if (score.hit()) {
                    hits++;
                }
                recall += score.recall();
                mrr += score.reciprocalRank();
                ndcg += score.ndcg();
            }
            curve.add(new RagKMetric(
                    k,
                    divide(hits, answerableCases.size()),
                    divide(recall, answerableCases.size()),
                    divide(mrr, answerableCases.size()),
                    divide(ndcg, answerableCases.size())));
        }
        return curve;
    }

    private RankingScore scoreRanking(
            RagCalibrationCase evaluationCase,
            List<RetrievedChunk> chunks,
            int k) {
        Set<Long> matchedLabels = new LinkedHashSet<>();
        int firstRelevantRank = 0;
        double dcg = 0.0;
        int relevantPositions = 0;

        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            if (isRelevant(evaluationCase, chunk)) {
                relevantPositions++;
                if (firstRelevantRank == 0) {
                    firstRelevantRank = index + 1;
                }
                dcg += 1.0 / log2(index + 2.0);
                matchedLabels.add(relevanceLabel(evaluationCase, chunk));
            }
        }

        int relevantLabelCount = !evaluationCase.relevantChunkIds().isEmpty()
                ? evaluationCase.relevantChunkIds().size()
                : evaluationCase.relevantDocumentIds().size();
        int idealCount = Math.min(relevantLabelCount, k);
        double idealDcg = 0.0;
        for (int index = 0; index < idealCount; index++) {
            idealDcg += 1.0 / log2(index + 2.0);
        }

        return new RankingScore(
                !matchedLabels.isEmpty(),
                divide(matchedLabels.size(), relevantLabelCount),
                firstRelevantRank == 0 ? 0.0 : 1.0 / firstRelevantRank,
                idealDcg == 0.0 ? 0.0 : dcg / idealDcg,
                divide(relevantPositions, chunks.size()));
    }

    private boolean isRelevant(
            RagCalibrationCase evaluationCase,
            RetrievedChunk chunk) {
        if (!evaluationCase.relevantChunkIds().isEmpty()) {
            return evaluationCase.relevantChunkIds().contains(chunk.getChunkId());
        }
        return evaluationCase.relevantDocumentIds().contains(chunk.getDocumentId());
    }

    private Long relevanceLabel(
            RagCalibrationCase evaluationCase,
            RetrievedChunk chunk) {
        return evaluationCase.relevantChunkIds().isEmpty()
                ? chunk.getDocumentId()
                : chunk.getChunkId();
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private double divide(double numerator, double denominator) {
        return denominator == 0.0 ? 0.0 : numerator / denominator;
    }

    private List<String> buildWarnings(
            RagQualityMetrics calibration,
            RagQualityMetrics test,
            boolean testMissing) {
        List<String> warnings = new ArrayList<>();
        if (testMissing) {
            warnings.add("没有独立TEST用例，本次结果只能用于调试，不能作为最终指标");
        }
        if (calibration.answerRecall() == 0.0) {
            warnings.add("零误答约束下没有任何有答案问题通过门控，说明正负距离分布严重重叠");
        }
        if (test != null && test.falseAcceptRate() > 0.0) {
            warnings.add("独立测试集仍出现误答，不能把校准集零误答理解成生产保证");
        }
        warnings.add("参数只对当前语料、切片方法和Embedding模型有效；变更后必须重新校准");
        return List.copyOf(warnings);
    }

    private record EvaluatedCase(
            RagCalibrationCase evaluationCase,
            List<RetrievedChunk> candidates) {
    }

    private record ThresholdScore(
            double threshold,
            int truePositive,
            int falsePositive) {
    }

    private record ConfigurationScore(
            RagTuningConfiguration configuration,
            RagQualityMetrics metrics) {
    }

    private record RankingScore(
            boolean hit,
            double recall,
            double reciprocalRank,
            double ndcg,
            double precision) {
    }
}
