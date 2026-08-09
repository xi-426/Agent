package com.yan.agent.chat;

import com.yan.agent.common.AiConfigurationException;
import com.yan.agent.workorder.WorkOrderConfirmationService;
import com.yan.agent.workorder.WorkOrderCreationParser;
import com.yan.agent.workorder.WorkOrderTools;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiChatService {

    private static final String PLACEHOLDER_API_KEY = "demo-key-not-configured";
    private static final String CONFIRM_WORK_ORDER_COMMAND = "确认创建工单";
    private static final String CANCEL_WORK_ORDER_COMMAND = "取消创建工单";
    private static final String WORK_ORDER_SYSTEM_PROMPT = """
            你是企业知识库与工单助手。
            创建、新建或提交工单由 Java 程序在调用模型前处理。
            你不得声称已经准备或已经创建工单。
            只有用户完整输入“确认创建工单”后，Java 程序才会正式写入数据库；
            不得把“确认”“确认创建”等其他表达声称为创建成功。
            查询和统计工单时必须调用对应工具，不得编造工单数据。
            """;

    private final ChatClient chatClient;
    private final RedisChatMemoryService memoryService;
    private final ChatHistoryService historyService;
    private final ChatSessionService sessionService;
    private final ChatRateLimitService rateLimitService;
    private final WorkOrderTools workOrderTools;
    private final WorkOrderConfirmationService confirmationService;
    private final WorkOrderCreationParser workOrderCreationParser;
    private final String apiKey;

    public AiChatService(
            ChatClient chatClient,
            RedisChatMemoryService memoryService,
            ChatHistoryService historyService,
            ChatSessionService sessionService,
            ChatRateLimitService rateLimitService,
            WorkOrderTools workOrderTools,
            WorkOrderConfirmationService confirmationService,
            WorkOrderCreationParser workOrderCreationParser,
            @Value("${spring.ai.openai.api-key}") String apiKey) {
        this.chatClient = chatClient;
        this.memoryService = memoryService;
        this.historyService = historyService;
        this.sessionService = sessionService;
        this.rateLimitService = rateLimitService;
        this.workOrderTools = workOrderTools;
        this.confirmationService = confirmationService;
        this.workOrderCreationParser = workOrderCreationParser;
        this.apiKey = apiKey;
    }

    public String chat(String userMessage) {
        ensureApiKeyConfigured();

        ChatClient.ChatClientRequestSpec request = chatClient
                .prompt()
                .user(userMessage);

        ChatClient.CallResponseSpec response = request.call();
        return response.content();
    }

    public Flux<String> streamChat(String userMessage) {
        ensureApiKeyConfigured();

        ChatClient.ChatClientRequestSpec request = chatClient
                .prompt()
                .user(userMessage);

        ChatClient.StreamResponseSpec response = request.stream();
        return response.content();
    }

    public String chatWithMemory(
            Long userId,
            Long sessionId,
            String userMessage) {
        ensureApiKeyConfigured();

        // 调用模型前确认 PostgreSQL 中存在这个会话。
        ChatSession session = sessionService.requireOwnedBy(
                sessionId,
                userId);
        rateLimitService.checkAllowed(userId);
        String commandAnswer = handleWorkOrderCommand(
                session,
                userMessage);

        if (commandAnswer != null) {
            saveTurn(
                    sessionId,
                    userMessage,
                    commandAnswer);
            return commandAnswer;
        }

        String preparationAnswer = handleWorkOrderPreparation(
                session,
                userMessage);

        if (preparationAnswer != null) {
            saveTurn(
                    sessionId,
                    userMessage,
                    preparationAnswer);
            return preparationAnswer;
        }

        List<ChatMemoryMessage> memoryMessages = memoryService.getRecentMessages(
                sessionId);

        if (memoryMessages.isEmpty()) {

            memoryMessages = historyService.loadRecentMessages(
                    sessionId);

            for (ChatMemoryMessage memoryMessage : memoryMessages) {

                memoryService.append(
                        sessionId,
                        memoryMessage.getRole(),
                        memoryMessage.getContent());
            }
        }

        List<Message> history = toSpringAiMessages(
                memoryMessages);

        ChatClient.ChatClientRequestSpec request = chatClient
                .prompt()
                .system(WORK_ORDER_SYSTEM_PROMPT)
                .messages(history)
                .user(userMessage)
                .tools(workOrderTools)
                .toolContext(
                        Map.of(
                                WorkOrderTools.USER_ID_CONTEXT_KEY,
                                session.getUserId(),
                                WorkOrderTools.SESSION_ID_CONTEXT_KEY,
                                session.getId()));

        ChatClient.CallResponseSpec response = request.call();

        String answer = response.content();

        if (answer.contains(CONFIRM_WORK_ORDER_COMMAND)
                && !confirmationService.hasPending(
                        session.getUserId(),
                        session.getId())) {
            answer = """
                    工单没有成功进入待确认状态，因此尚未创建。
                    请重新发送完整的工单标题、描述和优先级；系统会在真正写入 Redis 后再要求确认。
                    """;
        }

        // 先把本轮 USER/ASSISTANT 作为一个事务永久保存到 PostgreSQL。
        saveTurn(
                sessionId,
                userMessage,
                answer);

        return answer;
    }

    private String handleWorkOrderCommand(
            ChatSession session,
            String userMessage) {
        // 精确识别“确认创建工单”和“取消创建工单”；其他消息返回 null。
        String command = userMessage.trim();

        if (CONFIRM_WORK_ORDER_COMMAND.equals(command)) {
            return confirmationService.confirm(
                    session.getUserId(),
                    session.getId());
        }

        if ("确认".equals(command)
                || "确认创建".equals(command)) {
            if (confirmationService.hasPending(
                    session.getUserId(),
                    session.getId())) {
                return "为避免误操作，请回复完整的“确认创建工单”。";
            }
            return "当前没有等待确认的工单，请先发送完整的创建要求。";
        }

        if (CANCEL_WORK_ORDER_COMMAND.equals(command)) {
            confirmationService.cancel(
                    session.getUserId(),
                    session.getId());

            return "已取消创建工单。";
        }

        return null;
    }

    private String handleWorkOrderPreparation(
            ChatSession session,
            String userMessage) {
        if (!workOrderCreationParser.isCreateIntent(userMessage)) {
            return null;
        }

        return workOrderCreationParser.parse(userMessage)
                .map(draft -> confirmationService.prepare(
                        session.getUserId(),
                        session.getId(),
                        draft.title(),
                        draft.description(),
                        draft.priority()))
                .orElse("""
                        创建工单需要完整的标题、描述和优先级。
                        请按以下格式发送：
                        帮我创建一个高优先级工单，标题是“标题内容”，描述是“详细描述”。
                        """);
    }

    private void saveTurn(
            Long sessionId,
            String userMessage,
            String assistantMessage) {
        historyService.saveTurn(
                sessionId,
                userMessage,
                assistantMessage);

        memoryService.append(
                sessionId,
                ChatMemoryMessage.Role.USER,
                userMessage);

        memoryService.append(
                sessionId,
                ChatMemoryMessage.Role.ASSISTANT,
                assistantMessage);
    }

    private List<Message> toSpringAiMessages(
            List<ChatMemoryMessage> memoryMessages) {
        List<Message> messages = new ArrayList<>();

        for (ChatMemoryMessage memoryMessage : memoryMessages) {

            if (memoryMessage.getRole() == ChatMemoryMessage.Role.USER) {

                messages.add(
                        new UserMessage(
                                memoryMessage.getContent()));

            } else if (memoryMessage.getRole() == ChatMemoryMessage.Role.ASSISTANT) {

                messages.add(
                        new AssistantMessage(
                                memoryMessage.getContent()));

            } else {
                throw new IllegalArgumentException(
                        "当前会话记忆暂不支持角色："
                                + memoryMessage.getRole());
            }
        }

        return messages;
    }

    private void ensureApiKeyConfigured() {
        if (apiKey.isBlank() || PLACEHOLDER_API_KEY.equals(apiKey)) {
            throw new AiConfigurationException(
                    "尚未配置DEEPSEEK_API_KEY，请先阅读README中的启动步骤");
        }
    }
}
