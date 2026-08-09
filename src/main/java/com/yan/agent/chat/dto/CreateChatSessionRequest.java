package com.yan.agent.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateChatSessionRequest {



    @NotBlank(message = "会话标题不能为空")
    @Size(max = 200, message = "会话标题不能超过200个字符")
    private String title;

    public CreateChatSessionRequest() {
    }


    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
