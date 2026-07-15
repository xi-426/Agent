package com.yan.agent.common;

import java.time.Instant;

public class ApiError {

    private final int status;
    private final String message;
    private final Instant timestamp;

    public ApiError(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}

