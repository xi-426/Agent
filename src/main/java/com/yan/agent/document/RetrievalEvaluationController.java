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

    private final RagCalibrationService calibrationService;

    public RetrievalEvaluationController(
            RagCalibrationService calibrationService) {
        this.calibrationService = calibrationService;
    }

    @PostMapping("/calibration")
    public ResponseEntity<RagCalibrationResult> calibrate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long knowledgeBaseId,
            @Valid @RequestBody RagCalibrationRequest request) {
        Number ownerIdClaim = jwt.getClaim("userId");
        RagCalibrationResult result = calibrationService.calibrate(
                ownerIdClaim.longValue(),
                knowledgeBaseId,
                request);
        return ResponseEntity.ok(result);
    }
}
