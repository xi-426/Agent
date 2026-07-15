package com.yan.agent.chat;

import com.yan.agent.chat.dto.ChatRequest;
import com.yan.agent.chat.dto.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final AiChatService chatService;

    public ChatController(AiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String answer = chatService.chat(request.getMessage());
        ChatResponse response = new ChatResponse(answer);
        return ResponseEntity.ok(response);
    }
}

