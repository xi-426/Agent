package com.yan.agent.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RagCalibrationRequest(
        @Min(value = 2, message = "实验检索上限不能小于2")
        @Max(value = 20, message = "实验检索上限不能超过20") int maxCandidateK,
        @NotEmpty(message = "校准用例不能为空")
        @Size(max = 500, message = "一次最多校准500个问题")
        List<@Valid RagCalibrationCase> cases) {
}
