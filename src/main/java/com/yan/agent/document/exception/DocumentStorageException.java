package com.yan.agent.document.exception;

public class DocumentStorageException
        extends RuntimeException {

    public DocumentStorageException(
            String message,
            Throwable cause) {
        super(message, cause);
    }
}