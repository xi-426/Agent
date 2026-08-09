package com.yan.agent;

import com.yan.agent.workorder.WorkOrder;
import com.yan.agent.workorder.WorkOrderCreationParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderCreationParserTest {

    private final WorkOrderCreationParser parser =
            new WorkOrderCreationParser();

    @Test
    void shouldParseCompleteChineseCreationRequest() {
        String message = """
                帮我创建一个高优先级工单，标题是“新版工作台验收”，\
                描述是“验证登录、会话记忆、知识库和 RAG 功能”。
                """;

        WorkOrderCreationParser.WorkOrderDraft draft =
                parser.parse(message).orElseThrow();

        assertThat(draft.title())
                .isEqualTo("新版工作台验收");
        assertThat(draft.description())
                .isEqualTo("验证登录、会话记忆、知识库和 RAG 功能");
        assertThat(draft.priority())
                .isEqualTo(WorkOrder.Priority.HIGH);
    }

    @Test
    void shouldRejectIncompleteCreationRequest() {
        assertThat(parser.parse("帮我创建一个工单"))
                .isEmpty();
        assertThat(parser.isCreateIntent("查询待处理工单"))
                .isFalse();
    }
}
