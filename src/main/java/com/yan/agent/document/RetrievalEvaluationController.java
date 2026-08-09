package com.yan.agent.document;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/v1/knowledge-bases/{knowledgeBaseId}/evaluations")
public class RetrievalEvaluationController {

    private final RetrievalEvaluationService evaluationService;

    public RetrievalEvaluationController(
            RetrievalEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/retrieval")
    public ResponseEntity<RetrievalEvaluationResult>
            evaluateRetrieval(
                    @AuthenticationPrincipal Jwt jwt,
                    @PathVariable Long knowledgeBaseId,
                    @Valid @RequestBody
                    RetrievalEvaluationRequest request) {

        Number ownerIdClaim = jwt.getClaim("userId");
        Long ownerId = ownerIdClaim.longValue();

        RetrievalEvaluationResult result =
                evaluationService.evaluate(
                        ownerId,
                        knowledgeBaseId,
                        request.cases());

        return ResponseEntity.ok(result);
    }
}