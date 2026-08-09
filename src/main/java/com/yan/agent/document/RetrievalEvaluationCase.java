package com.yan.agent.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RetrievalEvaluationCase(

        @NotBlank(message = "评测编号不能为空") String id,

        @NotBlank(message = "评测问题不能为空") @Size(max = 500, message = "评测问题不能超过500个字符") String question,

        @Size(max = 255, message = "期望文档名不能超过255个字符") String expectedDocumentName,

        boolean expectAnswer) {
}