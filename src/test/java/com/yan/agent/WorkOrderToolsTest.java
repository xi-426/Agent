package com.yan.agent;

import com.yan.agent.workorder.WorkOrder;
import com.yan.agent.workorder.WorkOrderConfirmationService;
import com.yan.agent.workorder.WorkOrderRepository;
import com.yan.agent.workorder.WorkOrderTools;
import com.yan.agent.chat.ChatSession;
import com.yan.agent.chat.ChatSessionService;
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
class WorkOrderToolsTest {

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkOrderTools workOrderTools;

    @Autowired
    private WorkOrderConfirmationService confirmationService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ChatSessionService sessionService;

    @Test
    void shouldQueryAndCountOnlyCurrentUsersOpenWorkOrders() {
        // 为用户7保存两条 OPEN 工单，通过 ToolContext 查询并统计，再断言结果。
        WorkOrder first = workOrderRepository.save(
                new WorkOrder(
                        7L,
                        "无法登录系统",
                        "用户输入正确密码后仍然无法登录",
                        WorkOrder.Priority.HIGH));

        WorkOrder second = workOrderRepository.save(
                new WorkOrder(
                        7L,
                        "知识库上传失败",
                        "上传PDF时页面提示处理失败",
                        WorkOrder.Priority.MEDIUM));

        ToolContext toolContext = new ToolContext(
                Map.of(
                        WorkOrderTools.USER_ID_CONTEXT_KEY,
                        7L));

        List<WorkOrder> results = workOrderTools.queryWorkOrdersByStatus(
                WorkOrder.Status.OPEN,
                toolContext);

        long expectedCount = workOrderRepository
                .countByUserIdAndStatus(
                        7L,
                        WorkOrder.Status.OPEN);

        long toolCount = workOrderTools.countWorkOrdersByStatus(
                WorkOrder.Status.OPEN,
                toolContext);

        assertThat(results)
                .contains(first, second);

        assertThat(toolCount)
                .isEqualTo(expectedCount);
    }

    @Test
    void shouldPrepareConfirmAndClearPendingWorkOrder() {
        AppUser user = userRepository.save(new AppUser(
                "work-order-" + UUID.randomUUID() + "@example.com",
                "工单确认测试用户",
                "test-password-hash"));
        ChatSession session = sessionService.create(
                user.getId(),
                "工单确认测试会话");
        ToolContext toolContext = new ToolContext(
                Map.of(
                        WorkOrderTools.USER_ID_CONTEXT_KEY,
                        user.getId(),
                        WorkOrderTools.SESSION_ID_CONTEXT_KEY,
                        session.getId()));

        String prepareResult = workOrderTools.prepareCreateWorkOrder(
                "新版工作台验收",
                "验证确认流程",
                WorkOrder.Priority.HIGH,
                toolContext);

        assertThat(prepareResult).contains("确认创建工单");
        assertThat(confirmationService.hasPending(
                user.getId(),
                session.getId())).isTrue();

        String confirmResult = confirmationService.confirm(
                user.getId(),
                session.getId());

        assertThat(confirmResult).contains("工单创建成功");
        assertThat(confirmationService.hasPending(
                user.getId(),
                session.getId())).isFalse();
        assertThat(workOrderRepository.findByUserIdOrderByIdDesc(
                user.getId()))
                .extracting(WorkOrder::getTitle)
                .containsExactly("新版工作台验收");
    }
}
