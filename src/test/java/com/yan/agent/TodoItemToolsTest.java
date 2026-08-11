package com.yan.agent;

import com.yan.agent.chat.ChatSession;
import com.yan.agent.chat.ChatSessionService;
import com.yan.agent.todo.TodoItem;
import com.yan.agent.todo.TodoItemConfirmationService;
import com.yan.agent.todo.TodoItemRepository;
import com.yan.agent.todo.TodoItemTools;
import com.yan.agent.user.AppUser;
import com.yan.agent.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TodoItemToolsTest {

    @Autowired
    private TodoItemRepository todoItemRepository;

    @Autowired
    private TodoItemTools todoItemTools;

    @Autowired
    private TodoItemConfirmationService confirmationService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ChatSessionService sessionService;

    @Test
    void shouldQueryAndCountOnlyCurrentUsersPendingTodoItems() {
        TodoItem first = todoItemRepository.save(
                new TodoItem(
                        7L,
                        "复习RAG流程",
                        "整理检索、重排与拒答笔记",
                        TodoItem.Priority.HIGH));

        TodoItem second = todoItemRepository.save(
                new TodoItem(
                        7L,
                        "准备项目演示",
                        "验证上传、问答和来源展示",
                        TodoItem.Priority.MEDIUM));

        ToolContext toolContext = new ToolContext(
                Map.of(TodoItemTools.USER_ID_CONTEXT_KEY, 7L));

        List<TodoItem> results = todoItemTools.queryTodoItemsByStatus(
                TodoItem.Status.PENDING,
                toolContext);

        long expectedCount = todoItemRepository.countByUserIdAndStatus(
                7L,
                TodoItem.Status.PENDING);

        long toolCount = todoItemTools.countTodoItemsByStatus(
                TodoItem.Status.PENDING,
                toolContext);

        assertThat(results).contains(first, second);
        assertThat(toolCount).isEqualTo(expectedCount);
    }

    @Test
    void shouldPrepareConfirmAndClearPendingTodoItem() {
        AppUser user = userRepository.save(new AppUser(
                "todo-item-" + UUID.randomUUID() + "@example.com",
                "待办确认测试用户",
                "test-password-hash"));
        ChatSession session = sessionService.create(
                user.getId(),
                "待办确认测试会话");
        ToolContext toolContext = new ToolContext(
                Map.of(
                        TodoItemTools.USER_ID_CONTEXT_KEY, user.getId(),
                        TodoItemTools.SESSION_ID_CONTEXT_KEY, session.getId()));

        String prepareResult = todoItemTools.prepareCreateTodoItem(
                "复习RAG流程",
                "验证待办二次确认",
                TodoItem.Priority.HIGH,
                toolContext);

        assertThat(prepareResult).contains("确认创建待办");
        assertThat(confirmationService.hasPending(user.getId(), session.getId())).isTrue();

        String confirmResult = confirmationService.confirm(user.getId(), session.getId());

        assertThat(confirmResult).contains("待办事项创建成功");
        assertThat(confirmationService.hasPending(user.getId(), session.getId())).isFalse();
        assertThat(todoItemRepository.findByUserIdOrderByIdDesc(user.getId()))
                .extracting(TodoItem::getTitle)
                .containsExactly("复习RAG流程");
    }
}
