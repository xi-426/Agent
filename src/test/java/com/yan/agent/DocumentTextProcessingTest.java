package com.yan.agent;

import com.yan.agent.document.DocumentProperties;
import com.yan.agent.document.DocumentTextChunker;
import com.yan.agent.document.DocumentTextCleaner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTextProcessingTest {

    @Test
    void shouldCleanRepeatedWhitespace() {

        DocumentTextCleaner cleaner =
                new DocumentTextCleaner();

        String rawText =
                "标题\r\n\r\n\r\n  正文\t\t内容  ";

        String cleanedText =
                cleaner.clean(rawText);

        assertThat(cleanedText)
                .isEqualTo("标题\n\n正文 内容");
    }

    @Test
    void shouldSplitTextWithOverlap() {

        DocumentProperties properties =
                new DocumentProperties();

        properties.setChunkSize(10);
        properties.setChunkOverlap(3);

        DocumentTextChunker chunker =
                new DocumentTextChunker(properties);

        String text =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        List<String> chunks =
                chunker.split(text);

        assertThat(chunks)
                .containsExactly(
                        "ABCDEFGHIJ",
                        "HIJKLMNOPQ",
                        "OPQRSTUVWX",
                        "VWXYZ");
    }
}