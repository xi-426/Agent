package com.yan.agent.document;

import com.yan.agent.document.exception.DocumentParsingException;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class DocumentTextExtractor {

    private static final int MAX_TEXT_LENGTH =
            1_000_000;

    private final Tika tika;

    public DocumentTextExtractor() {
        this.tika = new Tika();
        this.tika.setMaxStringLength(
                MAX_TEXT_LENGTH);
    }

    public String extract(String storagePath) {

        Path path = Paths.get(storagePath);

        try (InputStream inputStream =
                     Files.newInputStream(path)) {

            //Tika 根据文件内容选择对应解析器
            String extractedText =
                    tika.parseToString(inputStream);

            if (extractedText == null
                    || extractedText.isBlank()) {
                throw new DocumentParsingException(
                        "文件中没有可提取的文字");
            }

            return extractedText;
        } catch (IOException | TikaException exception) {
            throw new DocumentParsingException(
                    "文档文字提取失败",
                    exception);
        }
    }
}