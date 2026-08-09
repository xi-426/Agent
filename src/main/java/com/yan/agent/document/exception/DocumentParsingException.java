package com.yan.agent.document.exception;

public class DocumentParsingException
        extends RuntimeException {

    public DocumentParsingException(String message) {
        super(message);
    }

    public DocumentParsingException(
            String message,
            Throwable cause) {
        super(message, cause);
    }
}