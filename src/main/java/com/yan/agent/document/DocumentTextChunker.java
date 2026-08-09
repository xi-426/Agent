package com.yan.agent.document;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentTextChunker {

    private final DocumentProperties properties;

    public DocumentTextChunker(
            DocumentProperties properties) {
        this.properties = properties;
    }

    public List<String> split(String text) {

        if (text == null || text.isBlank()) {
            return List.of();
        }

        int chunkSize = properties.getChunkSize();
        int overlap = properties.getChunkOverlap();

        validateConfiguration(chunkSize, overlap);

        List<String> chunks = new ArrayList<>();

        int start = 0;

        while (start < text.length()) {

            int preferredEnd = Math.min(
                    start + chunkSize,
                    text.length());

            int end = findNaturalEnd(
                    text,
                    start,
                    preferredEnd);

            String chunk = text
                    .substring(start, end)
                    .trim();

            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end == text.length()) {
                break;
            }

            start = findNextStart(
                    text,
                    start,
                    end,
                    overlap);
        }

        return chunks;
    }

    private int findNaturalEnd(
            String text,
            int start,
            int preferredEnd) {

        if (preferredEnd == text.length()) {
            return preferredEnd;
        }

        /*
         * start是第0个字，你原本计划在第800个字的位置把文本切开，也就是preferredEnd = 800。
         * 
         * 代码里的minimumEnd = start + (preferredEnd - start)/2，套入这个例子就是 0 + (800 - 0)/2 =
         * 400。
         * 它的意思是：我不会从第800个字一直往回找到第0个字，我只允许在「400字~800字」这个后半段区间里找合适的切分点。
         * 这么做的目的很简单：如果真的一路往回找到第100个字（假设）才找到句号，那切出来这块文本就只有100字，太短了，完全不符合我们想要"每块大概800字"的分块要求，
         * 这个规则就是避免切出来的块缩水太严重。
         * 
         * 
         */
        int minimumEnd = start + (preferredEnd - start) / 2;

        int paragraphEnd = text.lastIndexOf(
                "\n\n",
                preferredEnd - 1);

        if (paragraphEnd >= minimumEnd) {
            return paragraphEnd + 2;
        }

        int lineEnd = text.lastIndexOf(
                '\n',
                preferredEnd - 1);

        if (lineEnd >= minimumEnd) {
            return lineEnd + 1;
        }

        for (int index = preferredEnd - 1; index >= minimumEnd; index--) {

            char currentCharacter = text.charAt(index);

            if (isSentenceEnd(currentCharacter)) {
                return index + 1;
            }
        }

        return preferredEnd;
    }

    private int findNextStart(
            String text,
            int previousStart,
            int end,
            int overlap) {

        int desiredStart = Math.max(
                end - overlap,
                previousStart + 1);

        int searchEnd = Math.min(
                desiredStart + 80,
                end);

        for (int index = desiredStart;
             index < searchEnd;
             index++) {

            char previousCharacter =
                    text.charAt(index - 1);

            if (previousCharacter == '\n'
                    || previousCharacter == ' '
                    || isSentenceEnd(previousCharacter)) {
                return index;
            }
        }

        return desiredStart;
    }

    private boolean isSentenceEnd(
            char character) {

        return character == '。'
                || character == '！'
                || character == '？'
                || character == '.'
                || character == '!'
                || character == '?';
    }

    private void validateConfiguration(
            int chunkSize,
            int overlap) {

        if (chunkSize <= 0) {
            throw new IllegalStateException(
                    "chunk-size必须大于0");
        }

        if (overlap < 0
                || overlap >= chunkSize) {
            throw new IllegalStateException(
                    "chunk-overlap必须大于等于0且小于chunk-size");
        }
    }
}
