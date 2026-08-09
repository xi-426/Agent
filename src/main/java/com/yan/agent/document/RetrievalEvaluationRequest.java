package com.yan.agent.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RetrievalEvaluationRequest(

        @NotEmpty(message = "评测用例不能为空") @Size(max = 50, message = "一次最多评测50个问题") List<@Valid RetrievalEvaluationCase> cases) {
}