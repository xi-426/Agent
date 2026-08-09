package com.yan.agent.document;

import com.yan.agent.document.exception.DocumentStorageException;
import com.yan.agent.document.exception.InvalidDocumentException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentStorageService {

    private final Path storageRoot;

    public DocumentStorageService(
            DocumentProperties properties) {

        this.storageRoot = Paths
                .get(properties.getStorageRoot())
                .toAbsolutePath()
                .normalize();

        createStorageDirectory();
    }

    public String store(MultipartFile file) {

        String originalName = file.getOriginalFilename();

        if (originalName == null) {
            throw new InvalidDocumentException(
                    "文件名不能为空");
        }

        String extension =
                extractExtension(originalName);

        String storedFileName =
                UUID.randomUUID() + "." + extension;

        //resolve() 是 Path 对象的方法，用来把一个子路径接到当前路径后面。
        Path targetPath = storageRoot
                .resolve(storedFileName)
                .normalize();

        if (!targetPath.startsWith(storageRoot)) {
            throw new InvalidDocumentException(
                    "非法文件路径");
        }

        try (InputStream inputStream =
                     file.getInputStream()) {

            Files.copy(inputStream, targetPath);
            return targetPath.toString();
        } catch (IOException exception) {
            throw new DocumentStorageException(
                    "保存上传文件失败",
                    exception);
        }
    }

    private void createStorageDirectory() {
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException exception) {
            throw new DocumentStorageException(
                    "无法创建文件存储目录",
                    exception);
        }
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