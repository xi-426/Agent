package com.yan.agent.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKnowledgeBaseRequest(

        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 100, message = "知识库名称不能超过100个字符")
        String name,

        @Size(max = 2000, message = "知识库描述不能超过2000个字符")
        String description) {
}