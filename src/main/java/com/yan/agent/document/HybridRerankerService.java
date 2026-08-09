package com.yan.agent.document;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class HybridRerankerService {

    private static final double VECTOR_WEIGHT = 0.8;
    private static final double LEXICAL_WEIGHT = 0.2;

    public List<RetrievedChunk> rerank(
            String question,
            List<RetrievedChunk> candidates,
            int limit) {
        List<ScoredChunk> scoredChunks = new ArrayList<>();

        for (RetrievedChunk candidate : candidates) {
            double vectorSimilarity = 1.0 - candidate.getDistance();
            double lexicalCoverage = calculateLexicalCoverage(
                    question,
                    candidate.getContent());
            double finalScore = VECTOR_WEIGHT * vectorSimilarity
                    + LEXICAL_WEIGHT * lexicalCoverage;

            scoredChunks.add(new ScoredChunk(candidate, finalScore));
        }

        scoredChunks.sort(new Comparator<ScoredChunk>() {
            @Override
            public int compare(ScoredChunk first, ScoredChunk second) {
                return Double.compare(second.score, first.score);
            }
        });

        List<RetrievedChunk> reranked = new ArrayList<>();
        int resultSize = Math.min(limit, scoredChunks.size());

        for (int index = 0; index < resultSize; index++) {
            reranked.add(scoredChunks.get(index).chunk);
        }

        return reranked;
    }

    private double calculateLexicalCoverage(
            String question,
            String content) {
        String normalizedQuestion = normalize(question);
        String normalizedContent = normalize(content);

        if (normalizedQuestion.isEmpty()) {
            return 0.0;
        }

        if (normalizedQuestion.length() == 1) {
            return normalizedContent.contains(normalizedQuestion)
                    ? 1.0
                    : 0.0;
        }

        Set<String> questionBigrams = new HashSet<>();

        for (int index = 0;
                index < normalizedQuestion.length() - 1;
                index++) {
            questionBigrams.add(normalizedQuestion.substring(
                    index,
                    index + 2));
        }

        int matchedBigrams = 0;
        for (String bigram : questionBigrams) {
            if (normalizedContent.contains(bigram)) {
                matchedBigrams++;
            }
        }

        return (double) matchedBigrams / questionBigrams.size();
    }

    private String normalize(String text) {
        return text
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}a-z0-9]", "");
    }

    private static class ScoredChunk {
        private final RetrievedChunk chunk;
        private final double score;

        private ScoredChunk(RetrievedChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
