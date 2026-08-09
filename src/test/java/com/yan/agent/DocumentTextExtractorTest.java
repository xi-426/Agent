package com.yan.agent;

import com.yan.agent.document.DocumentTextExtractor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTextExtractorTest {

    @Test
    void shouldExtractTextFromMarkdownFile() {

        DocumentTextExtractor extractor =
                new DocumentTextExtractor();

        Path documentPath = Paths.get(
                "sample-documents",
                "00_公司治理与经营原则.md");

        String extractedText =
                extractor.extract(
                        documentPath.toString());

        assertThat(extractedText)
                .contains("本项目虚构示例公司")
                .contains("客户价值优先");
    }
}