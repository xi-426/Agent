package com.yan.agent;

import com.yan.agent.todo.TodoItem;
import com.yan.agent.todo.TodoItemCreationParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TodoItemCreationParserTest {

    private final TodoItemCreationParser parser = new TodoItemCreationParser();

    @Test
    void shouldParseCompleteChineseCreationRequest() {
        String message = """
                帮我创建一个高优先级待办事项，标题是“复习RAG流程”，\
                描述是“整理知识库检索与拒答笔记”。
                """;

        TodoItemCreationParser.TodoItemDraft draft =
                parser.parse(message).orElseThrow();

        assertThat(draft.title()).isEqualTo("复习RAG流程");
        assertThat(draft.description()).isEqualTo("整理知识库检索与拒答笔记");
        assertThat(draft.priority()).isEqualTo(TodoItem.Priority.HIGH);
    }

    @Test
    void shouldRejectIncompleteCreationRequest() {
        assertThat(parser.parse("帮我创建一个待办事项")).isEmpty();
        assertThat(parser.isCreateIntent("查询待处理的待办事项")).isFalse();
    }
}
