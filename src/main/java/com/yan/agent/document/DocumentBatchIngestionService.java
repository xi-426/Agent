package com.yan.agent.document;

import com.yan.agent.document.exception.DocumentParsingException;
import com.yan.agent.document.exception.DocumentStorageException;
import com.yan.agent.document.exception.InvalidDocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentBatchIngestionService {

    private static final Logger log = LoggerFactory.getLogger(
            DocumentBatchIngestionService.class);

    private final DocumentIngestionService ingestionService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentProperties properties;

    public DocumentBatchIngestionService(
            DocumentIngestionService ingestionService,
            KnowledgeBaseService knowledgeBaseService,
            DocumentProperties properties) {
        this.ingestionService = ingestionService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.properties = properties;
    }

    public BatchDocumentUploadResponse ingestBatch(
            Long ownerId,
            Long knowledgeBaseId,
            List<MultipartFile> files) {
        validateBatch(files);

        // 在处理任何文件前完成一次所有权校验，避免把越权错误包装成文件失败。
        knowledgeBaseService.requireOwnedBy(knowledgeBaseId, ownerId);

        List<BatchDocumentUploadItemResponse> items = new ArrayList<>();
        int successCount = 0;

        // 顺序处理，避免一次并发多个Embedding请求压垮本地Ollama。
        for (MultipartFile file : files) {
            String originalName = displayName(file);
            try {
                KnowledgeDocument document = ingestionService.ingest(
                        ownerId,
                        knowledgeBaseId,
                        file);
                items.add(BatchDocumentUploadItemResponse.success(document));
                successCount++;
            } catch (InvalidDocumentException | DocumentParsingException exception) {
                items.add(BatchDocumentUploadItemResponse.failure(
                        originalName,
                        exception.getMessage()));
            } catch (DocumentStorageException exception) {
                log.error("Batch document storage failed: {}", originalName, exception);
                items.add(BatchDocumentUploadItemResponse.failure(
                        originalName,
                        "服务器无法保存该文件"));
            } catch (RuntimeException exception) {
                log.error("Batch document processing failed: {}", originalName, exception);
                items.add(BatchDocumentUploadItemResponse.failure(
                        originalName,
                        "文档处理失败，请查看后端日志"));
            }
        }

        return new BatchDocumentUploadResponse(
                files.size(),
                successCount,
                files.size() - successCount,
                items);
    }

    private void validateBatch(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new InvalidDocumentException("请至少选择一个文件");
        }
        if (files.size() > properties.getMaxBatchFiles()) {
            throw new InvalidDocumentException(
                    "单次最多上传" + properties.getMaxBatchFiles() + "个文件");
        }

        long totalSize = 0;
        for (MultipartFile file : files) {
            if (file != null) {
                totalSize += file.getSize();
            }
        }
        if (totalSize > properties.getMaxBatchTotalSizeBytes()) {
            long maxMegabytes = properties.getMaxBatchTotalSizeBytes()
                    / 1024 / 1024;
            throw new InvalidDocumentException(
                    "单次上传文件总大小不能超过" + maxMegabytes + "MB");
        }
    }

    private String displayName(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null
                || file.getOriginalFilename().isBlank()) {
            return "未命名文件";
        }
        return file.getOriginalFilename();
    }
}
