package com.yan.agent;

import com.yan.agent.document.DocumentProperties;
import com.yan.agent.document.DocumentTextChunker;
import com.yan.agent.document.DocumentTextCleaner;
import com.yan.agent.document.DocumentTextExtractor;
import com.yan.agent.document.RagCalibrationCase;
import com.yan.agent.document.RagCalibrationRequest;
import com.yan.agent.document.RagEvaluationSplit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只在本地手动运行的切片参数实验。
 *
 * <p>它故意不使用距离门控、词法规则、Reranker 或聊天模型，只比较不同切片配置下
 * bge-m3 原始余弦距离排序，避免把切片效果和后续参数混在一起。</p>
 *
 * <p>运行：</p>
 * <pre>
 * .\mvnw.cmd -Dtest=LocalPureVectorChunkingExperimentTest \
 *   -DrunChunkingExperiment=true test
 * </pre>
 */
@EnabledIfSystemProperty(
        named = "runChunkingExperiment",
        matches = "true")
class LocalPureVectorChunkingExperimentTest {

    private static final Path DOCUMENT_ROOT =
            Path.of("private-knowledge");
    private static final Path DOCUMENT_MAP_PATH =
            Path.of("local-evaluation-data", "school-document-map.json");
    private static final Path EVALUATION_PATH =
            Path.of("local-evaluation-data", "school-rag-calibration.json");
    private static final Path OUTPUT_PATH =
            Path.of(
                    "local-evaluation-data",
                    "school-rag-chunking-pure-vector-result.json");
    private static final int MAX_EVALUATION_K = 20;
    private static final int EMBEDDING_BATCH_SIZE = 16;
    private static final ChunkConfiguration PRODUCTION_CONFIGURATION =
            new ChunkConfiguration(800, 120);

    private static final List<ChunkConfiguration> CONFIGURATIONS = List.of(
            new ChunkConfiguration(400, 0),
            new ChunkConfiguration(400, 65),
            new ChunkConfiguration(400, 94),
            new ChunkConfiguration(400, 167),
            new ChunkConfiguration(700, 0),
            new ChunkConfiguration(700, 65),
            new ChunkConfiguration(700, 94),
            new ChunkConfiguration(700, 167),
            new ChunkConfiguration(900, 0),
            new ChunkConfiguration(900, 65),
            new ChunkConfiguration(900, 94),
            new ChunkConfiguration(900, 167),
            new ChunkConfiguration(1800, 0),
            new ChunkConfiguration(1800, 65),
            new ChunkConfiguration(1800, 94),
            new ChunkConfiguration(1800, 167),
            new ChunkConfiguration(800, 120));

    private final DocumentTextExtractor textExtractor =
            new DocumentTextExtractor();
    private final DocumentTextCleaner textCleaner =
            new DocumentTextCleaner();
    private final EmbeddingModel embeddingModel = createEmbeddingModel();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCompareSeventeenChunkingConfigurationsUsingPureVectorRetrieval()
            throws Exception {
        assertThat(DOCUMENT_ROOT).isDirectory();
        assertThat(DOCUMENT_MAP_PATH).isRegularFile();
        assertThat(EVALUATION_PATH).isRegularFile();

        Map<Long, String> documentFiles = readDocumentMap();
        RagCalibrationRequest evaluation = objectMapper.readValue(
                Files.readString(EVALUATION_PATH),
                RagCalibrationRequest.class);
        validateDocumentLabels(evaluation.cases(), documentFiles.keySet());

        Map<Long, String> cleanedDocuments = loadAndCleanDocuments(documentFiles);
        // 17 组配置比较阶段只准备 CALIBRATION 问题向量，TEST 此时完全不参与。
        Map<String, float[]> calibrationQuestionEmbeddings = embedQuestions(
                evaluation.cases(),
                RagEvaluationSplit.CALIBRATION);

        List<ConfigurationResult> allResults = new ArrayList<>();
        for (ChunkConfiguration configuration : CONFIGURATIONS) {
            List<EmbeddedChunk> chunks = createEmbeddedChunks(
                    configuration,
                    cleanedDocuments);
            SplitResult calibration = evaluateSplit(
                    RagEvaluationSplit.CALIBRATION,
                    evaluation.cases(),
                    calibrationQuestionEmbeddings,
                    chunks);
            allResults.add(new ConfigurationResult(
                    configuration,
                    chunks.size(),
                    chunks.stream()
                            .mapToInt(chunk -> chunk.content().length())
                            .average()
                            .orElse(0.0),
                    calibration));
        }

        ConfigurationResult bestByRankingMetrics = allResults.stream()
                .max(rankingComparator())
                .orElseThrow();
        ConfigurationResult productionConfiguration = allResults.stream()
                .filter(result -> result.configuration()
                        .equals(PRODUCTION_CONFIGURATION))
                .findFirst()
                .orElseThrow();

        // 生产配置已经锁定为 800/120 后，TEST 才首次参与实验，并且只评估这一组。
        Map<String, float[]> testQuestionEmbeddings = embedQuestions(
                evaluation.cases(),
                RagEvaluationSplit.TEST);
        List<EmbeddedChunk> productionChunks = createEmbeddedChunks(
                PRODUCTION_CONFIGURATION,
                cleanedDocuments);
        SplitResult productionTest = evaluateSplit(
                RagEvaluationSplit.TEST,
                evaluation.cases(),
                testQuestionEmbeddings,
                productionChunks);

        double contextReduction = 1.0
                - productionConfiguration.calibration()
                .at8().averageContextCharacters()
                / bestByRankingMetrics.calibration()
                .at8().averageContextCharacters();
        String productionDecision = String.format(
                "800/120与排名指标最佳的%d/%d在CALIBRATION上的Hit@3、Hit@8相同，"
                        + "但Top8平均上下文字符减少约%.1f%%；因此保留800/120作为检索质量、证据粒度和Prompt成本之间的工程折中，而不是宣称它是唯一最优。",
                bestByRankingMetrics.configuration().chunkSize(),
                bestByRankingMetrics.configuration().overlap(),
                contextReduction * 100.0);

        ExperimentResult result = new ExperimentResult(
                "PURE_VECTOR_CHUNKING_V2",
                "17组配置只使用CALIBRATION有答案题比较；锁定800/120后，TEST仅对该生产配置运行一次；全程不使用距离门控、Reranker、词法分数或聊天模型",
                "排名指标先比较MRR@20，再比较Hit@8、Hit@3；它只产生bestByRankingMetrics，不直接替代包含上下文成本的生产决策",
                "跨配置使用人工相关文档ID；chunk ID会随切片变化，因此不参与跨配置比较",
                productionDecision,
                CONFIGURATIONS.size(),
                documentFiles.size(),
                evaluation.cases().size(),
                bestByRankingMetrics,
                productionConfiguration,
                productionTest,
                List.copyOf(allResults));

        Files.createDirectories(OUTPUT_PATH.getParent());
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(OUTPUT_PATH.toFile(), result);

        assertThat(allResults).hasSize(17);
        assertThat(bestByRankingMetrics.configuration())
                .isEqualTo(new ChunkConfiguration(1800, 65));
        assertThat(bestByRankingMetrics.calibration().answerableCases())
                .isEqualTo(24);
        assertThat(productionConfiguration.configuration())
                .isEqualTo(PRODUCTION_CONFIGURATION);
        assertThat(productionTest.answerableCases()).isEqualTo(12);
    }

    private EmbeddingModel createEmbeddingModel() {
        String baseUrl = System.getenv().getOrDefault(
                "OLLAMA_BASE_URL",
                "http://localhost:11434");
        String model = System.getenv().getOrDefault(
                "OLLAMA_EMBEDDING_MODEL",
                "bge-m3");
        ReactorClientHttpRequestFactory requestFactory =
                new ReactorClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofMinutes(5));

        return OllamaEmbeddingModel.builder()
                .ollamaApi(OllamaApi.builder()
                        .baseUrl(baseUrl)
                        .restClientBuilder(RestClient.builder()
                                .requestFactory(requestFactory))
                        .build())
                .options(OllamaEmbeddingOptions.builder()
                        .model(model)
                        .build())
                .build();
    }

    private Map<Long, String> readDocumentMap() throws Exception {
        Map<String, String> raw = objectMapper.readValue(
                Files.readString(DOCUMENT_MAP_PATH),
                new TypeReference<>() {
                });
        Map<Long, String> mapped = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            mapped.put(Long.valueOf(entry.getKey()), entry.getValue());
        }
        return Map.copyOf(mapped);
    }

    private void validateDocumentLabels(
            List<RagCalibrationCase> cases,
            Set<Long> availableDocumentIds) {
        for (RagCalibrationCase evaluationCase : cases) {
            if (evaluationCase.expectAnswer()) {
                assertThat(evaluationCase.relevantDocumentIds())
                        .as("有答案用例必须有跨切片稳定的文档标签：%s", evaluationCase.id())
                        .isNotEmpty()
                        .isSubsetOf(availableDocumentIds);
            }
        }
    }

    private Map<Long, String> loadAndCleanDocuments(
            Map<Long, String> documentFiles) {
        Map<Long, String> documents = new LinkedHashMap<>();
        for (Map.Entry<Long, String> entry : documentFiles.entrySet()) {
            Path path = DOCUMENT_ROOT.resolve(entry.getValue());
            assertThat(path).isRegularFile();
            documents.put(
                    entry.getKey(),
                    textCleaner.clean(textExtractor.extract(path.toString())));
        }
        return Map.copyOf(documents);
    }

    private Map<String, float[]> embedQuestions(
            List<RagCalibrationCase> cases,
            RagEvaluationSplit split) {
        List<RagCalibrationCase> answerableCases = cases.stream()
                .filter(RagCalibrationCase::expectAnswer)
                .filter(evaluationCase -> evaluationCase.split() == split)
                .toList();
        List<float[]> embeddings = embedInBatches(
                answerableCases.stream()
                        .map(RagCalibrationCase::question)
                        .toList());
        Map<String, float[]> result = new LinkedHashMap<>();
        for (int index = 0; index < answerableCases.size(); index++) {
            result.put(answerableCases.get(index).id(), embeddings.get(index));
        }
        return Map.copyOf(result);
    }

    private List<EmbeddedChunk> createEmbeddedChunks(
            ChunkConfiguration configuration,
            Map<Long, String> documents) {
        DocumentProperties properties = new DocumentProperties();
        properties.setChunkSize(configuration.chunkSize());
        properties.setChunkOverlap(configuration.overlap());
        DocumentTextChunker chunker = new DocumentTextChunker(properties);

        List<UnembeddedChunk> unembedded = new ArrayList<>();
        for (Map.Entry<Long, String> document : documents.entrySet()) {
            List<String> texts = chunker.split(document.getValue());
            for (int index = 0; index < texts.size(); index++) {
                unembedded.add(new UnembeddedChunk(
                        document.getKey(),
                        index,
                        texts.get(index)));
            }
        }

        List<float[]> embeddings = embedInBatches(
                unembedded.stream()
                        .map(UnembeddedChunk::content)
                        .toList());
        List<EmbeddedChunk> chunks = new ArrayList<>();
        for (int index = 0; index < unembedded.size(); index++) {
            UnembeddedChunk chunk = unembedded.get(index);
            chunks.add(new EmbeddedChunk(
                    chunk.documentId(),
                    chunk.chunkIndex(),
                    chunk.content(),
                    embeddings.get(index)));
        }
        return List.copyOf(chunks);
    }

    private List<float[]> embedInBatches(List<String> texts) {
        List<float[]> result = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, texts.size());
            result.addAll(embeddingModel.embed(texts.subList(start, end)));
        }
        return List.copyOf(result);
    }

    private SplitResult evaluateSplit(
            RagEvaluationSplit split,
            List<RagCalibrationCase> cases,
            Map<String, float[]> questionEmbeddings,
            List<EmbeddedChunk> chunks) {
        List<RagCalibrationCase> answerable = cases.stream()
                .filter(RagCalibrationCase::expectAnswer)
                .filter(evaluationCase -> evaluationCase.split() == split)
                .toList();

        List<List<RankedChunk>> rankings = new ArrayList<>();
        List<Integer> firstRelevantRanks = new ArrayList<>();
        for (RagCalibrationCase evaluationCase : answerable) {
            List<RankedChunk> ranked = chunks.stream()
                    .map(chunk -> new RankedChunk(
                            chunk.documentId(),
                            chunk.chunkIndex(),
                            chunk.content().length(),
                            cosineDistance(
                                    questionEmbeddings.get(evaluationCase.id()),
                                    chunk.embedding())))
                    .sorted(Comparator.comparingDouble(RankedChunk::distance))
                    .limit(MAX_EVALUATION_K)
                    .toList();
            rankings.add(ranked);
            firstRelevantRanks.add(firstRelevantRank(
                    evaluationCase.relevantDocumentIds(),
                    ranked));
        }

        List<KMetric> curve = new ArrayList<>();
        for (int k = 1; k <= MAX_EVALUATION_K; k++) {
            int hits = 0;
            double reciprocalRankSum = 0.0;
            double contextCharactersSum = 0.0;
            for (int caseIndex = 0; caseIndex < rankings.size(); caseIndex++) {
                int rank = firstRelevantRanks.get(caseIndex);
                if (rank > 0 && rank <= k) {
                    hits++;
                    reciprocalRankSum += 1.0 / rank;
                }
                contextCharactersSum += rankings.get(caseIndex).stream()
                        .limit(k)
                        .mapToInt(RankedChunk::characters)
                        .sum();
            }
            curve.add(new KMetric(
                    k,
                    divide(hits, answerable.size()),
                    divide(reciprocalRankSum, answerable.size()),
                    divide(contextCharactersSum, answerable.size())));
        }

        return new SplitResult(
                split.name(),
                answerable.size(),
                metricAt(curve, 1),
                metricAt(curve, 3),
                metricAt(curve, 8),
                metricAt(curve, 20),
                List.copyOf(curve));
    }

    private int firstRelevantRank(
            List<Long> relevantDocumentIds,
            List<RankedChunk> ranked) {
        for (int index = 0; index < ranked.size(); index++) {
            if (relevantDocumentIds.contains(ranked.get(index).documentId())) {
                return index + 1;
            }
        }
        return 0;
    }

    private Comparator<ConfigurationResult> rankingComparator() {
        return Comparator
                .comparingDouble((ConfigurationResult result) ->
                        result.calibration().at20().mrr())
                .thenComparingDouble(result ->
                        result.calibration().at8().hit())
                .thenComparingDouble(result ->
                        result.calibration().at3().hit())
                .thenComparingDouble(result ->
                        -result.calibration().at8().averageContextCharacters())
                .thenComparingInt(result -> -result.totalChunks());
    }

    private KMetric metricAt(List<KMetric> curve, int k) {
        return curve.get(k - 1);
    }

    private double cosineDistance(float[] left, float[] right) {
        assertThat(left).hasSameSizeAs(right);
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 1.0;
        }
        return 1.0 - dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double divide(double numerator, double denominator) {
        return denominator == 0.0 ? 0.0 : numerator / denominator;
    }

    private record ChunkConfiguration(int chunkSize, int overlap) {
    }

    private record UnembeddedChunk(
            Long documentId,
            int chunkIndex,
            String content) {
    }

    private record EmbeddedChunk(
            Long documentId,
            int chunkIndex,
            String content,
            float[] embedding) {
    }

    private record RankedChunk(
            Long documentId,
            int chunkIndex,
            int characters,
            double distance) {
    }

    private record KMetric(
            int k,
            double hit,
            double mrr,
            double averageContextCharacters) {
    }

    private record SplitResult(
            String split,
            int answerableCases,
            KMetric at1,
            KMetric at3,
            KMetric at8,
            KMetric at20,
            List<KMetric> curve) {
    }

    private record ConfigurationResult(
            ChunkConfiguration configuration,
            int totalChunks,
            double averageChunkCharacters,
            SplitResult calibration) {
    }

    private record ExperimentResult(
            String experimentVersion,
            String isolationPolicy,
            String rankingPolicy,
            String relevancePolicy,
            String productionDecision,
            int configurationCount,
            int documentCount,
            int evaluationCaseCount,
            ConfigurationResult bestByRankingMetrics,
            ConfigurationResult productionConfiguration,
            SplitResult productionTest,
            List<ConfigurationResult> allCalibrationResults) {
    }
}
