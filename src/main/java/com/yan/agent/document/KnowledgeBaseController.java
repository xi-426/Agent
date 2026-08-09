package com.yan.agent.document;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) {
        this.service = service;
    }

    // 增加使用 JWT 创建当前用户知识库的 POST 接口。
    @PostMapping
    public ResponseEntity<KnowledgeBaseResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateKnowledgeBaseRequest request) {

        Number ownerIdClaim = jwt.getClaim("userId");
        Long ownerId = ownerIdClaim.longValue();

        KnowledgeBase savedKnowledgeBase = service.create(
                ownerId,
                request.name(),
                request.description());

        KnowledgeBaseResponse response = new KnowledgeBaseResponse(
                savedKnowledgeBase.getId(),
                savedKnowledgeBase.getOwnerId(),
                savedKnowledgeBase.getName(),
                savedKnowledgeBase.getDescription());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public List<KnowledgeBaseResponse> list(
            @AuthenticationPrincipal Jwt jwt) {
        Number ownerIdClaim = jwt.getClaim("userId");
        Long ownerId = ownerIdClaim.longValue();

        List<KnowledgeBaseResponse> responses = new ArrayList<>();
        for (KnowledgeBase knowledgeBase : service.findOwnedBy(ownerId)) {
            responses.add(new KnowledgeBaseResponse(
                    knowledgeBase.getId(),
                    knowledgeBase.getOwnerId(),
                    knowledgeBase.getName(),
                    knowledgeBase.getDescription()));
        }
        return responses;
    }
}
