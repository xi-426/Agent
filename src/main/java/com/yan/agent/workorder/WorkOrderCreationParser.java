package com.yan.agent.workorder;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WorkOrderCreationParser {

    private static final Pattern CREATE_INTENT = Pattern.compile(
            "(创建|新建|提交).{0,10}工单|工单.{0,10}(创建|新建|提交)");

    private static final Pattern TITLE = Pattern.compile(
            "标题\\s*(?:是|为|[:：])\\s*(.+?)\\s*[,，]\\s*描述");

    private static final Pattern DESCRIPTION = Pattern.compile(
            "描述\\s*(?:是|为|[:：])\\s*(.+?)\\s*[。.]?$");

    public boolean isCreateIntent(String message) {
        return message != null
                && CREATE_INTENT.matcher(message).find();
    }

    public Optional<WorkOrderDraft> parse(String message) {
        if (!isCreateIntent(message)) {
            return Optional.empty();
        }

        Matcher titleMatcher = TITLE.matcher(message);
        Matcher descriptionMatcher = DESCRIPTION.matcher(message);
        WorkOrder.Priority priority = parsePriority(message);

        if (!titleMatcher.find()
                || !descriptionMatcher.find()
                || priority == null) {
            return Optional.empty();
        }

        String title = stripQuotes(titleMatcher.group(1));
        String description = stripQuotes(descriptionMatcher.group(1));

        if (title.isBlank() || description.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new WorkOrderDraft(
                title,
                description,
                priority));
    }

    private WorkOrder.Priority parsePriority(String message) {
        String upperMessage = message.toUpperCase();

        if (message.contains("紧急")
                || upperMessage.contains("URGENT")) {
            return WorkOrder.Priority.URGENT;
        }
        if (message.contains("高优先级")
                || upperMessage.contains("HIGH")) {
            return WorkOrder.Priority.HIGH;
        }
        if (message.contains("中优先级")
                || upperMessage.contains("MEDIUM")) {
            return WorkOrder.Priority.MEDIUM;
        }
        if (message.contains("低优先级")
                || upperMessage.contains("LOW")) {
            return WorkOrder.Priority.LOW;
        }
        return null;
    }

    private String stripQuotes(String value) {
        String result = value.trim();
        while (result.length() >= 2
                && isOpeningQuote(result.charAt(0))
                && isClosingQuote(
                        result.charAt(result.length() - 1))) {
            result = result.substring(
                    1,
                    result.length() - 1).trim();
        }
        return result;
    }

    private boolean isOpeningQuote(char character) {
        return character == '“'
                || character == '"'
                || character == '\''
                || character == '「'
                || character == '『';
    }

    private boolean isClosingQuote(char character) {
        return character == '”'
                || character == '"'
                || character == '\''
                || character == '」'
                || character == '』';
    }

    public record WorkOrderDraft(
            String title,
            String description,
            WorkOrder.Priority priority) {
    }
}
