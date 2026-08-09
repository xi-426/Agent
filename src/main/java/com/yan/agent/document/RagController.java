package com.yan.agent.document;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/v1/knowledge-bases/{knowledgeBaseId}/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public ResponseEntity<RagResult> ask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long knowledgeBaseId,
            @Valid @RequestBody RagRequest request) {
        Number ownerIdClaim = jwt.getClaim("userId");
        Long ownerId = ownerIdClaim.longValue();
        RagResult result = ragService.answer(
                ownerId,
                knowledgeBaseId,
                request.getQuestion());

        return ResponseEntity.ok(result);
    }
}
