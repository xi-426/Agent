package com.yan.agent.todo;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TodoItemCreationParser {

    private static final Pattern CREATE_INTENT = Pattern.compile(
            "(创建|新建|添加).{0,10}待办(?:事项)?|待办(?:事项)?.{0,10}(创建|新建|添加)");

    private static final Pattern TITLE = Pattern.compile(
            "标题\\s*(?:是|为|[:：])\\s*(.+?)\\s*[,，]\\s*描述");

    private static final Pattern DESCRIPTION = Pattern.compile(
            "描述\\s*(?:是|为|[:：])\\s*(.+?)\\s*[。.]?$");

    public boolean isCreateIntent(String message) {
        return message != null && CREATE_INTENT.matcher(message).find();
    }

    public Optional<TodoItemDraft> parse(String message) {
        if (!isCreateIntent(message)) {
            return Optional.empty();
        }

        Matcher titleMatcher = TITLE.matcher(message);
        Matcher descriptionMatcher = DESCRIPTION.matcher(message);
        TodoItem.Priority priority = parsePriority(message);

        if (!titleMatcher.find() || !descriptionMatcher.find() || priority == null) {
            return Optional.empty();
        }

        String title = stripQuotes(titleMatcher.group(1));
        String description = stripQuotes(descriptionMatcher.group(1));
        if (title.isBlank() || description.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new TodoItemDraft(title, description, priority));
    }

    private TodoItem.Priority parsePriority(String message) {
        String upperMessage = message.toUpperCase();
        if (message.contains("紧急") || upperMessage.contains("URGENT")) {
            return TodoItem.Priority.URGENT;
        }
        if (message.contains("高优先级") || upperMessage.contains("HIGH")) {
            return TodoItem.Priority.HIGH;
        }
        if (message.contains("中优先级") || upperMessage.contains("MEDIUM")) {
            return TodoItem.Priority.MEDIUM;
        }
        if (message.contains("低优先级") || upperMessage.contains("LOW")) {
            return TodoItem.Priority.LOW;
        }
        return null;
    }

    private String stripQuotes(String value) {
        String result = value.trim();
        while (result.length() >= 2
                && isOpeningQuote(result.charAt(0))
                && isClosingQuote(result.charAt(result.length() - 1))) {
            result = result.substring(1, result.length() - 1).trim();
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

    public record TodoItemDraft(
            String title,
            String description,
            TodoItem.Priority priority) {
    }
}
