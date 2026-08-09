package com.yan.agent.chat;

import com.yan.agent.chat.dto.ChatRequest;
import com.yan.agent.chat.dto.ChatResponse;
import com.yan.agent.chat.dto.ChatSessionResponse;
import com.yan.agent.chat.dto.ChatMessageResponse;
import com.yan.agent.chat.dto.CreateChatSessionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final AiChatService chatService;
    private final ChatSessionService sessionService;
    private final ChatHistoryService historyService;

    public ChatController(
            AiChatService chatService,
            ChatSessionService sessionService,
            ChatHistoryService historyService) {
        this.chatService = chatService;
        this.sessionService = sessionService;
        this.historyService = historyService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String answer = chatService.chat(request.getMessage());
        ChatResponse response = new ChatResponse(answer);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/chat/sessions")
    public ResponseEntity<ChatSessionResponse> createSession(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateChatSessionRequest request) {

        Number userIdClaim = jwt.getClaim("userId");
        Long userId = userIdClaim.longValue();

        ChatSession savedSession = sessionService.create(
                userId,
                request.getTitle());

        ChatSessionResponse response = new ChatSessionResponse(
                savedSession.getId(),
                savedSession.getUserId(),
                savedSession.getTitle());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/chat/sessions")
    public List<ChatSessionResponse> listSessions(
            @AuthenticationPrincipal Jwt jwt) {
        Number userIdClaim = jwt.getClaim("userId");
        Long userId = userIdClaim.longValue();

        List<ChatSessionResponse> responses = new ArrayList<>();
        for (ChatSession session : sessionService.findOwnedBy(userId)) {
            responses.add(new ChatSessionResponse(
                    session.getId(),
                    session.getUserId(),
                    session.getTitle()));
        }
        return responses;
    }

    @GetMapping("/chat/sessions/{sessionId}/messages")
    public List<ChatMessageResponse> listMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long sessionId) {
        Number userIdClaim = jwt.getClaim("userId");
        Long userId = userIdClaim.longValue();
        sessionService.requireOwnedBy(sessionId, userId);

        List<ChatMessageResponse> responses = new ArrayList<>();
        for (ChatMessage message : historyService
                .loadRecentChatMessages(sessionId)) {
            responses.add(new ChatMessageResponse(
                    message.getId(),
                    message.getRole(),
                    message.getContent()));
        }
        return responses;
    }

    @PostMapping("/chat/sessions/{sessionId}")
    public ResponseEntity<ChatResponse> chatWithMemory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatRequest request) {

        Number userIdClaim = jwt.getClaim("userId");
        Long userId = userIdClaim.longValue();

        String answer = chatService.chatWithMemory(
                userId,
                sessionId,
                request.getMessage());

        ChatResponse response = new ChatResponse(answer);

        return ResponseEntity.ok(response);
    }

    // 表示相应类型为text/event-stream 也就是 SSE，中文可以理解为“服务器发送事件”。 SSE像保持一条运输通道，连接暂时不关→
    // 生成一个片段就发送一个片段→ 全部生成完成后才关闭连接
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@Valid @RequestBody ChatRequest request) {
        return chatService.streamChat(request.getMessage());
    }

    @GetMapping("/hello")
    public String health() {
        return "Agent项目已启动";
    }

}
