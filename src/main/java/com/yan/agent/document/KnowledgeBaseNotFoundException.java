package com.yan.agent.document;

public class KnowledgeBaseNotFoundException extends RuntimeException {

    // 接收 knowledgeBaseId 并生成统一错误消息。
    public KnowledgeBaseNotFoundException(
            Long knowledgeBaseId) {
        super("知识库不存在：" + knowledgeBaseId);
    }
}
