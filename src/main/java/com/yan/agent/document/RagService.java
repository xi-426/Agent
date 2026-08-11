package com.yan.agent.document;

import com.yan.agent.chat.AiChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

        private static final String INSUFFICIENT_ANSWER = "知识库中没有足够资料回答该问题。";

        private final KnowledgeRetrievalService retrievalService;
        private final AiChatService aiChatService;
        private final int topK;
        private final double maxDistance;
        private final KnowledgeBaseService knowledgeBaseService;

        public RagService(
                        KnowledgeRetrievalService retrievalService,
                        KnowledgeBaseService knowledgeBaseService,
                        AiChatService aiChatService,
                        @Value("${app.rag.top-k}") int topK,
                        @Value("${app.rag.max-distance}") double maxDistance) {
                this.retrievalService = retrievalService;
                this.knowledgeBaseService = knowledgeBaseService;
                this.aiChatService = aiChatService;
                this.topK = topK;
                this.maxDistance = maxDistance;
        }

        public RagResult answer(
                        Long ownerId,
                        Long knowledgeBaseId,
                        String question) {
                knowledgeBaseService.requireOwnedBy(
                                knowledgeBaseId,
                                ownerId);
                List<RetrievedChunk> candidates = retrievalService.retrieve(
                                knowledgeBaseId,
                                question,
                                topK);

                List<RetrievedChunk> acceptedChunks = candidates.stream()
                                .filter(chunk -> chunk.getDistance() <= maxDistance)
                                .toList();

                if (acceptedChunks.isEmpty()) {
                        return new RagResult(
                                        INSUFFICIENT_ANSWER,
                                        List.of());
                }

                String prompt = buildPrompt(
                                question,
                                acceptedChunks);

                String answer = aiChatService.chat(prompt);

                if (INSUFFICIENT_ANSWER.equals(answer.trim())) {
                        return new RagResult(
                                        INSUFFICIENT_ANSWER,
                                        List.of());
                }

                return new RagResult(
                                answer,
                                acceptedChunks);
        }

        private String buildPrompt(
                        String question,
                        List<RetrievedChunk> chunks) {
                StringBuilder context = new StringBuilder();

                for (int index = 0; index < chunks.size(); index++) {

                        RetrievedChunk chunk = chunks.get(index);

                        context.append("[资料")
                                        .append(index + 1)
                                        .append("]\n");

                        context.append("文件：")
                                        .append(chunk.getDocumentName())
                                        .append("\n");

                        context.append("切片序号：")
                                        .append(chunk.getChunkIndex())
                                        .append("\n");

                        context.append("正文：\n")
                                        .append(chunk.getContent())
                                        .append("\n\n");
                }

                return """
                                请严格遵守以下回答规则：
                                1. 只能根据提供的知识库资料回答。
                                2. 不得补充资料中没有的事实。
                                3. 使用资料时必须在对应内容后标注[资料1]、[资料2]。
                                4. 如果资料不足以回答，必须回答：知识库中没有足够资料回答该问题。

                                知识库资料：
                                %s

                                用户问题：
                                %s
                                """.formatted(
                                context.toString(),
                                question);
        }
}
