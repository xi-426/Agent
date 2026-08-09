package com.yan.agent.common;

import java.time.Instant;

import org.slf4j.MDC;

public class ApiError {

    private final int status;
    private final String message;
    private final Instant timestamp;
    private final String requestId;

    public ApiError(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = Instant.now();
        this.requestId = MDC.get(RequestLoggingFilter.REQUEST_ID_KEY);
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

    public String getRequestId() {
        return requestId;
    }
}
