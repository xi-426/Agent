package com.yan.agent.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RagRequest {

    @NotBlank(message = "问题不能为空")
    @Size(min = 2, max = 1000, message = "问题长度必须在2到1000字符之间")
    private String question;

    public RagRequest() {
    }

    public RagRequest(String question) {
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
