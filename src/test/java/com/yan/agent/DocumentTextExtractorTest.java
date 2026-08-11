package com.yan.agent;

import com.yan.agent.document.DocumentTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTextExtractorTest {

    @Test
    void shouldExtractTextFromMarkdownFile() throws IOException {

        DocumentTextExtractor extractor =
                new DocumentTextExtractor();

        Path targetDirectory = Path.of("target", "test-generated");
        Files.createDirectories(targetDirectory);
        Path documentPath = Files.createTempFile(
                targetDirectory,
                "notes-",
                ".md");

        try {
            Files.writeString(
                    documentPath,
                    "# 课程通知\n\n奖学金材料请在截止日期前提交。");

            String extractedText = extractor.extract(
                    documentPath.toString());

            assertThat(extractedText)
                    .contains("课程通知")
                    .contains("奖学金材料请在截止日期前提交");
        } finally {
            Files.deleteIfExists(documentPath);
        }
    }
}
