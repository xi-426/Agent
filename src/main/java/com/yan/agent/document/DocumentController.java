package com.yan.agent.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class DocumentController {

        private final DocumentIngestionService ingestionService;
        private final KnowledgeBaseService knowledgeBaseService;
        private final KnowledgeDocumentRepository documentRepository;

        public DocumentController(
                        DocumentIngestionService ingestionService,
                        KnowledgeBaseService knowledgeBaseService,
                        KnowledgeDocumentRepository documentRepository) {
                this.ingestionService = ingestionService;
                this.knowledgeBaseService = knowledgeBaseService;
                this.documentRepository = documentRepository;
        }

        // @PathVariable 的意思是：这个参数来自 URL 路径中的变量
        // @RequestParam("file") MultipartFile file表示从 multipart 表单中寻找名字为 file 的部分：
        // consumes = MediaType.MULTIPART_FORM_DATA_VALUE这个接口只接收 multipart/form-data 请求。
        @PostMapping(value = "/{knowledgeBaseId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<DocumentUploadResponse> uploadDocument(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable Long knowledgeBaseId,
                        @RequestParam("file") MultipartFile file) {
                Number ownerIdClaim = jwt.getClaim("userId");
                Long ownerId = ownerIdClaim.longValue();
                KnowledgeDocument savedDocument = ingestionService.ingest(
                                ownerId,
                                knowledgeBaseId,
                                file);

                DocumentUploadResponse response = new DocumentUploadResponse(
                                savedDocument.getId(),
                                savedDocument.getOriginalName(),
                                savedDocument.getStatus());

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(response);
        }

        @GetMapping("/{knowledgeBaseId}/documents")
        public List<DocumentResponse> listDocuments(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable Long knowledgeBaseId) {
                Number ownerIdClaim = jwt.getClaim("userId");
                Long ownerId = ownerIdClaim.longValue();
                knowledgeBaseService.requireOwnedBy(knowledgeBaseId, ownerId);

                List<DocumentResponse> responses = new ArrayList<>();
                for (KnowledgeDocument document : documentRepository
                                .findByKnowledgeBaseIdOrderByIdDesc(knowledgeBaseId)) {
                        responses.add(new DocumentResponse(
                                        document.getId(),
                                        document.getOriginalName(),
                                        document.getContentType(),
                                        document.getSizeBytes(),
                                        document.getStatus()));
                }
                return responses;
        }
}
