package com.yan.agent.chat.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void shouldRejectBlankMessage() {
        ChatRequest request = new ChatRequest("   ");

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("消息不能为空");
    }

    @Test
    void shouldAcceptNormalMessage() {
        ChatRequest request = new ChatRequest("请解释什么是RAG");

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}

