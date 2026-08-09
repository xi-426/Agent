package com.yan.agent.document;

import com.yan.agent.document.exception.DocumentParsingException;
import com.yan.agent.document.exception.InvalidDocumentException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentIngestionService {

        private final DocumentFileValidator validator;
        private final DocumentStorageService storageService;
        private final KnowledgeDocumentRepository documentRepository;
        private final DocumentTextExtractor textExtractor;
        private final DocumentTextCleaner textCleaner;
        private final DocumentTextChunker textChunker;
        private final DocumentChunkRepository chunkRepository;
        private final DocumentEmbeddingService embeddingService;
        private final DocumentVectorRepository vectorRepository;
        private final KnowledgeBaseService knowledgeBaseService;

        public DocumentIngestionService(
                        DocumentFileValidator validator,
                        DocumentStorageService storageService,
                        KnowledgeDocumentRepository documentRepository,
                        DocumentTextExtractor textExtractor,
                        DocumentTextCleaner textCleaner,
                        DocumentTextChunker textChunker,
                        DocumentChunkRepository chunkRepository,
                        DocumentEmbeddingService embeddingService,
                        DocumentVectorRepository vectorRepository,
                        KnowledgeBaseService knowledgeBaseService) {

                this.validator = validator;
                this.storageService = storageService;
                this.documentRepository = documentRepository;
                this.textExtractor = textExtractor;
                this.textCleaner = textCleaner;
                this.textChunker = textChunker;
                this.chunkRepository = chunkRepository;
                this.embeddingService = embeddingService;
                this.vectorRepository = vectorRepository;
                this.knowledgeBaseService = knowledgeBaseService;
        }

        public KnowledgeDocument ingest(
                        Long ownerId,
                        Long knowledgeBaseId,
                        MultipartFile file) {
                knowledgeBaseService.requireOwnedBy(
                                knowledgeBaseId,
                                ownerId);

                String detectedContentType = validator.validateAndDetect(file);

                String storagePath = storageService.store(file);

                String originalName = file.getOriginalFilename();

                if (originalName == null) {
                        throw new InvalidDocumentException(
                                        "文件名不能为空");
                }

                // 这里只是在 Java 内存中创建对象：document.id == null
                KnowledgeDocument document = new KnowledgeDocument(
                                knowledgeBaseId,
                                originalName,
                                storagePath,
                                detectedContentType,
                                file.getSize());

                // 保存到 document 表，数据库生成文档 ID：savedDocument.id != null
                KnowledgeDocument savedDocument = documentRepository.save(document);

                try {
                        savedDocument.markParsing();
                        documentRepository.save(savedDocument);

                        String rawText = textExtractor.extract(storagePath);

                        String cleanedText = textCleaner.clean(rawText);

                        List<String> chunkTexts = textChunker.split(cleanedText);

                        if (chunkTexts.isEmpty()) {
                                throw new DocumentParsingException(
                                                "文档没有产生有效文本块");
                        }

                        List<DocumentChunk> chunks = createChunks(
                                        savedDocument.getId(),
                                        chunkTexts);

                        List<DocumentChunk> savedChunks = chunkRepository.saveAll(chunks);

                        saveEmbeddings(savedChunks);

                        savedDocument.markReady();

                        return documentRepository.save(
                                        savedDocument);
                } catch (RuntimeException exception) {
                        savedDocument.markFailed();
                        documentRepository.save(savedDocument);

                        throw exception;
                }
        }

        private List<DocumentChunk> createChunks(
                        Long documentId,
                        List<String> chunkTexts) {

                List<DocumentChunk> chunks = new ArrayList<>();

                for (int index = 0; index < chunkTexts.size(); index++) {

                        String chunkText = chunkTexts.get(index);

                        DocumentChunk chunk = new DocumentChunk(
                                        documentId,
                                        index,
                                        chunkText);

                        chunks.add(chunk);
                }

                return chunks;
        }

        private void saveEmbeddings(
                        List<DocumentChunk> chunks) {
                for (DocumentChunk chunk : chunks) {
                        String content = chunk.getContent();

                        float[] embedding = embeddingService.embed(content);

                        vectorRepository.saveEmbedding(
                                        chunk.getId(),
                                        embedding);
                }
        }
}
