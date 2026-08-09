package com.yan.agent.workorder;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorkOrderTools {

    public static final String USER_ID_CONTEXT_KEY = "userId";
    public static final String SESSION_ID_CONTEXT_KEY = "sessionId";

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderConfirmationService confirmationService;

    public WorkOrderTools(
            WorkOrderRepository workOrderRepository,
            WorkOrderConfirmationService confirmationService) {
        this.workOrderRepository = workOrderRepository;
        this.confirmationService = confirmationService;
    }

    @Tool(description = "查询当前用户指定状态的工单。用户询问待处理、处理中、已解决或已关闭工单时使用。")
    public List<WorkOrder> queryWorkOrdersByStatus(
            @ToolParam(description = "工单状态，只能是 OPEN、PROCESSING、RESOLVED 或 CLOSED") WorkOrder.Status status,
            ToolContext toolContext) {
        // status 由模型决定，userId 由 Java 通过 ToolContext 安全传入。
        // 从 ToolContext 安全取得当前用户 ID，再按 userId 和 status 查询。
        Object userIdValue = toolContext.getContext()
                .get(USER_ID_CONTEXT_KEY);

        if (!(userIdValue instanceof Number)) {
            throw new IllegalStateException(
                    "工具上下文中缺少用户ID");
        }

        Number userIdNumber = (Number) userIdValue;

        Long userId = userIdNumber.longValue();

        return workOrderRepository
                .findByUserIdAndStatusOrderByIdDesc(
                        userId,
                        status);
    }

    @Tool(description = "统计当前用户指定状态的工单数量。用户询问有多少待处理、处理中、已解决或已关闭工单时使用。")
    public long countWorkOrdersByStatus(
            @ToolParam(description = "工单状态，只能是 OPEN、PROCESSING、RESOLVED 或 CLOSED") WorkOrder.Status status,
            ToolContext toolContext) {
        // 取得可信 userId，再按用户和状态统计。
        Long userId = requireUserId(toolContext);

        return workOrderRepository
                .countByUserIdAndStatus(
                        userId,
                        status);
    }

    private Long requireUserId(ToolContext toolContext) {
        // 从 ToolContext 读取并校验 userId。
        Object userIdValue = toolContext.getContext()
                .get(USER_ID_CONTEXT_KEY);

        if (!(userIdValue instanceof Number)) {
            throw new IllegalStateException(
                    "工具上下文中缺少用户ID");
        }

        Number userIdNumber = (Number) userIdValue;

        return userIdNumber.longValue();
    }

    public String prepareCreateWorkOrder(
            String title,
            String description,
            WorkOrder.Priority priority,
            ToolContext toolContext) {
        // 从 ToolContext 取得 userId、sessionId，再调用 confirmationService.prepare。
        Long userId = requireUserId(toolContext);

        Object sessionIdValue = toolContext.getContext()
                .get(SESSION_ID_CONTEXT_KEY);

        if (!(sessionIdValue instanceof Number)) {
            throw new IllegalStateException(
                    "工具上下文中缺少会话ID");
        }

        Number sessionIdNumber = (Number) sessionIdValue;

        Long sessionId = sessionIdNumber.longValue();

        return confirmationService.prepare(
                userId,
                sessionId,
                title,
                description,
                priority);
    }
}
