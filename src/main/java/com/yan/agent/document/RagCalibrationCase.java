package com.yan.agent.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RagCalibrationCase(
        @NotBlank(message = "评测编号不能为空") String id,
        @NotBlank(message = "评测问题不能为空")
        @Size(max = 500, message = "评测问题不能超过500个字符") String question,
        @NotNull(message = "评测集划分不能为空") RagEvaluationSplit split,
        boolean expectAnswer,
        List<@Positive(message = "相关文档ID必须为正数") Long> relevantDocumentIds,
        List<@Positive(message = "相关切片ID必须为正数") Long> relevantChunkIds) {

    public RagCalibrationCase {
        relevantDocumentIds = relevantDocumentIds == null
                ? List.of()
                : List.copyOf(relevantDocumentIds);
        relevantChunkIds = relevantChunkIds == null
                ? List.of()
                : List.copyOf(relevantChunkIds);
    }
}
