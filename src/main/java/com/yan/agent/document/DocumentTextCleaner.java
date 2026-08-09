package com.yan.agent.document;

import org.springframework.stereotype.Component;

@Component
public class DocumentTextCleaner {

    public String clean(String rawText) {

        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String cleanedText = rawText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ');

        cleanedText = cleanedText
                .replaceAll("[\\t ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return cleanedText;
    }
}