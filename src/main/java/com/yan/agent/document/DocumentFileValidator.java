package com.yan.agent.document;

import com.yan.agent.document.exception.InvalidDocumentException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

@Component
public class DocumentFileValidator {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "docx", "txt", "md", "markdown");

    private final DocumentProperties properties;
    private final Tika tika;

    public DocumentFileValidator(
            DocumentProperties properties) {
        this.properties = properties;
        this.tika = new Tika();
    }

    public String validateAndDetect(
            MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException(
                    "上传文件不能为空");
        }

        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new InvalidDocumentException(
                    "上传文件不能超过10MB");
        }

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            throw new InvalidDocumentException(
                    "文件名不能为空");
        }

        String extension = extractExtension(originalName);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidDocumentException(
                    "只支持PDF、DOCX、TXT和Markdown文件");
        }

        String detectedType = detectContentType(
                file,
                originalName);

        if (!isTypeCompatible(extension, detectedType)) {
            throw new InvalidDocumentException(
                    "文件扩展名与真实内容类型不匹配");
        }

        return detectedType;
    }

    private String detectContentType(
            MultipartFile file,
            String originalName) {

        try (InputStream inputStream =
                     file.getInputStream()) {

            return tika.detect(
                    inputStream,
                    originalName);
        } catch (IOException exception) {
            throw new InvalidDocumentException(
                    "无法读取上传文件",
                    exception);
        }
    }

    private boolean isTypeCompatible(
            String extension,
            String contentType) {

        if ("pdf".equals(extension)) {
            return "application/pdf".equals(contentType);
        }

        if ("docx".equals(extension)) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        .equals(contentType);
        }

        if ("txt".equals(extension)) {
            return "text/plain".equals(contentType);
        }

        if ("md".equals(extension)
                || "markdown".equals(extension)) {
            return contentType.startsWith("text/");
        }

        return false;
    }

    private String extractExtension(
            String fileName) {

        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex < 0
                || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName
                .substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }
}