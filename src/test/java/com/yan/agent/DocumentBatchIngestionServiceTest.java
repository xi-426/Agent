package com.yan.agent;

import com.yan.agent.document.BatchDocumentUploadResponse;
import com.yan.agent.document.DocumentBatchIngestionService;
import com.yan.agent.document.DocumentIngestionService;
import com.yan.agent.document.DocumentProperties;
import com.yan.agent.document.DocumentStatus;
import com.yan.agent.document.KnowledgeBaseService;
import com.yan.agent.document.KnowledgeDocument;
import com.yan.agent.document.exception.InvalidDocumentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentBatchIngestionServiceTest {

    private DocumentIngestionService ingestionService;
    private KnowledgeBaseService knowledgeBaseService;
    private DocumentBatchIngestionService batchService;

    @BeforeEach
    void setUp() {
        ingestionService = mock(DocumentIngestionService.class);
        knowledgeBaseService = mock(KnowledgeBaseService.class);

        DocumentProperties properties = new DocumentProperties();
        properties.setMaxBatchFiles(20);
        properties.setMaxBatchTotalSizeBytes(50L * 1024 * 1024);

        batchService = new DocumentBatchIngestionService(
                ingestionService,
                knowledgeBaseService,
                properties);
    }

    @Test
    void shouldContinueWhenOneFileFails() {
        MockMultipartFile validFile = new MockMultipartFile(
                "files",
                "student-guide.md",
                "text/markdown",
                "真实内容".getBytes());
        MockMultipartFile invalidFile = new MockMultipartFile(
                "files",
                "malware.exe",
                "application/octet-stream",
                new byte[]{1, 2, 3});

        KnowledgeDocument savedDocument = mock(KnowledgeDocument.class);
        when(savedDocument.getId()).thenReturn(101L);
        when(savedDocument.getOriginalName()).thenReturn("student-guide.md");
        when(savedDocument.getStatus()).thenReturn(DocumentStatus.READY);
        when(ingestionService.ingest(7L, 16L, validFile))
                .thenReturn(savedDocument);
        when(ingestionService.ingest(7L, 16L, invalidFile))
                .thenThrow(new InvalidDocumentException("不支持该文件类型"));

        BatchDocumentUploadResponse response = batchService.ingestBatch(
                7L,
                16L,
                List.of(validFile, invalidFile));

        verify(knowledgeBaseService).requireOwnedBy(16L, 7L);
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).success()).isTrue();
        assertThat(response.items().get(1).success()).isFalse();
        assertThat(response.items().get(1).message())
                .isEqualTo("不支持该文件类型");
    }

    @Test
    void shouldRejectMoreThanTwentyFilesBeforeOwnershipCheck() {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "guide.md",
                "text/markdown",
                "内容".getBytes());

        assertThatThrownBy(() -> batchService.ingestBatch(
                7L,
                16L,
                List.of(file, file, file, file, file, file,
                        file, file, file, file, file, file,
                        file, file, file, file, file, file,
                        file, file, file)))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("单次最多上传20个文件");
    }
}
