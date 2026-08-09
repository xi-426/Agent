package com.yan.agent.chat;

public class TooManyChatRequestsException extends RuntimeException {

    public TooManyChatRequestsException(String message) {
        super(message);
    }
}
