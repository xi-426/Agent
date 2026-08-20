package com.yan.agent.chat;

import com.yan.agent.common.AiConfigurationException;
import com.yan.agent.todo.TodoItemConfirmationService;
import com.yan.agent.todo.TodoItemCreationParser;
import com.yan.agent.todo.TodoItemTools;

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
    private static final String CONFIRM_TODO_COMMAND = "确认创建待办";
    private static final String CANCEL_TODO_COMMAND = "取消创建待办";
    private static final String TODO_SYSTEM_PROMPT = """
            你是“知屿”个人知识库与待办助手。
            创建、新建或添加待办事项由 Java 程序在调用模型前处理。
            你不得声称已经准备或已经创建待办事项。
            只有用户完整输入“确认创建待办”后，Java 程序才会正式写入数据库；
            不得把“确认”“确认创建”等其他表达声称为创建成功。
            查询和统计待办事项时必须调用对应工具，不得编造待办数据。
            """;

    private final ChatClient chatClient;
    private final RedisChatMemoryService memoryService;
    private final ChatHistoryService historyService;
    private final ChatSessionService sessionService;
    private final ChatRateLimitService rateLimitService;
    private final TodoItemTools todoItemTools;
    private final TodoItemConfirmationService confirmationService;
    private final TodoItemCreationParser todoItemCreationParser;
    private final String apiKey;

    public AiChatService(
            ChatClient chatClient,
            RedisChatMemoryService memoryService,
            ChatHistoryService historyService,
            ChatSessionService sessionService,
            ChatRateLimitService rateLimitService,
            TodoItemTools todoItemTools,
            TodoItemConfirmationService confirmationService,
            TodoItemCreationParser todoItemCreationParser,
            @Value("${spring.ai.openai.api-key}") String apiKey) {
        this.chatClient = chatClient;
        this.memoryService = memoryService;
        this.historyService = historyService;
        this.sessionService = sessionService;
        this.rateLimitService = rateLimitService;
        this.todoItemTools = todoItemTools;
        this.confirmationService = confirmationService;
        this.todoItemCreationParser = todoItemCreationParser;
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
        String commandAnswer = handleTodoCommand(
                session,
                userMessage);

        if (commandAnswer != null) {
            saveTurn(
                    sessionId,
                    userMessage,
                    commandAnswer);
            return commandAnswer;
        }

        String preparationAnswer = handleTodoPreparation(
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
                .system(TODO_SYSTEM_PROMPT)
                .messages(history)
                .user(userMessage)
                .tools(todoItemTools)
                .toolContext(
                        Map.of(
                                TodoItemTools.USER_ID_CONTEXT_KEY,
                                session.getUserId()));

        ChatClient.CallResponseSpec response = request.call();

        String answer = response.content();

        if (answer.contains(CONFIRM_TODO_COMMAND)
                && !confirmationService.hasPending(
                        session.getUserId(),
                        session.getId())) {
            answer = """
                    待办事项没有成功进入待确认状态，因此尚未创建。
                    请重新发送完整的待办标题、描述和优先级；系统会在真正写入 Redis 后再要求确认。
                    """;
        }

        // 先把本轮 USER/ASSISTANT 作为一个事务永久保存到 PostgreSQL。
        saveTurn(
                sessionId,
                userMessage,
                answer);

        return answer;
    }

    private String handleTodoCommand(
            ChatSession session,
            String userMessage) {
        // 精确识别“确认创建待办”和“取消创建待办”；其他消息返回 null。
        String command = userMessage.trim();

        if (CONFIRM_TODO_COMMAND.equals(command)) {
            return confirmationService.confirm(
                    session.getUserId(),
                    session.getId());
        }

        if ("确认".equals(command)
                || "确认创建".equals(command)) {
            if (confirmationService.hasPending(
                    session.getUserId(),
                    session.getId())) {
                return "为避免误操作，请回复完整的“确认创建待办”。";
            }
            return "当前没有等待确认的待办事项，请先发送完整的创建要求。";
        }

        if (CANCEL_TODO_COMMAND.equals(command)) {
            confirmationService.cancel(
                    session.getUserId(),
                    session.getId());

            return "已取消创建待办事项。";
        }

        return null;
    }

    private String handleTodoPreparation(
            ChatSession session,
            String userMessage) {
        if (!todoItemCreationParser.isCreateIntent(userMessage)) {
            return null;
        }

        return todoItemCreationParser.parse(userMessage)
                .map(draft -> confirmationService.prepare(
                        session.getUserId(),
                        session.getId(),
                        draft.title(),
                        draft.description(),
                        draft.priority()))
                .orElse("""
                        创建待办事项需要完整的标题、描述和优先级。
                        请按以下格式发送：
                        帮我创建一个高优先级待办事项，标题是“标题内容”，描述是“详细描述”。
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
