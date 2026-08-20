package com.yan.agent.todo;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TodoItemTools {

    public static final String USER_ID_CONTEXT_KEY = "userId";

    private final TodoItemRepository todoItemRepository;

    public TodoItemTools(TodoItemRepository todoItemRepository) {
        this.todoItemRepository = todoItemRepository;
    }

    @Tool(description = "查询当前用户指定状态的待办事项。用户询问待处理、进行中、已完成或已取消的待办时使用。")
    public List<TodoItem> queryTodoItemsByStatus(
            @ToolParam(description = "待办状态，只能是 PENDING、IN_PROGRESS、COMPLETED 或 CANCELLED") TodoItem.Status status,
            ToolContext toolContext) {
        return todoItemRepository.findByUserIdAndStatusOrderByIdDesc(
                requireUserId(toolContext),
                status);
    }

    @Tool(description = "统计当前用户指定状态的待办事项数量。用户询问有多少待处理、进行中、已完成或已取消的待办时使用。")
    public long countTodoItemsByStatus(
            @ToolParam(description = "待办状态，只能是 PENDING、IN_PROGRESS、COMPLETED 或 CANCELLED") TodoItem.Status status,
            ToolContext toolContext) {
        return todoItemRepository.countByUserIdAndStatus(
                requireUserId(toolContext),
                status);
    }

    private Long requireUserId(ToolContext toolContext) {
        Object userIdValue = toolContext.getContext().get(USER_ID_CONTEXT_KEY);
        if (!(userIdValue instanceof Number userIdNumber)) {
            throw new IllegalStateException("工具上下文中缺少用户ID");
        }
        return userIdNumber.longValue();
    }

}
