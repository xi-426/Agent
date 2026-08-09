package com.yan.agent.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.yan.agent.document.exception.DocumentParsingException;
import com.yan.agent.document.exception.DocumentStorageException;
import com.yan.agent.document.exception.InvalidDocumentException;
import com.yan.agent.chat.ChatSessionNotFoundException;
import com.yan.agent.chat.TooManyChatRequestsException;
import com.yan.agent.auth.EmailAlreadyRegisteredException;
import com.yan.agent.auth.InvalidCredentialsException;
import com.yan.agent.document.KnowledgeBaseNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        @ExceptionHandler(AiConfigurationException.class)
        public ResponseEntity<ApiError> handleAiConfiguration(AiConfigurationException exception) {
                ApiError error = new ApiError(HttpStatus.SERVICE_UNAVAILABLE.value(), exception.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        }

        @ExceptionHandler(ChatSessionNotFoundException.class)
        public ResponseEntity<ApiError> handleChatSessionNotFound(
                        ChatSessionNotFoundException exception) {
                // 把会话不存在转换成 404 JSON 响应。
                ApiError error = new ApiError(
                                HttpStatus.NOT_FOUND.value(),
                                exception.getMessage());

                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(error);
        }

        @ExceptionHandler(TooManyChatRequestsException.class)
        public ResponseEntity<ApiError> handleTooManyChatRequests(
                        TooManyChatRequestsException exception) {
                ApiError error = new ApiError(
                                HttpStatus.TOO_MANY_REQUESTS.value(),
                                exception.getMessage());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
        }

        @ExceptionHandler(EmailAlreadyRegisteredException.class)
        public ResponseEntity<ApiError> handleEmailAlreadyRegistered(
                        EmailAlreadyRegisteredException exception) {
                ApiError error = new ApiError(HttpStatus.CONFLICT.value(), exception.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        @ExceptionHandler(InvalidCredentialsException.class)
        public ResponseEntity<ApiError> handleInvalidCredentials(
                        InvalidCredentialsException exception) {
                ApiError error = new ApiError(HttpStatus.UNAUTHORIZED.value(), exception.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
                String message = exception.getBindingResult()
                                .getFieldErrors()
                                .get(0)
                                .getDefaultMessage();

                ApiError error = new ApiError(HttpStatus.BAD_REQUEST.value(), message);
                return ResponseEntity.badRequest().body(error);
        }

        @ExceptionHandler(InvalidDocumentException.class)
        public ResponseEntity<ApiError> handleInvalidDocument(
                        InvalidDocumentException exception) {

                ApiError error = new ApiError(
                                HttpStatus.BAD_REQUEST.value(),
                                exception.getMessage());

                return ResponseEntity
                                .badRequest()
                                .body(error);
        }

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiError> handleMaxUploadSize(
                        MaxUploadSizeExceededException exception) {

                ApiError error = new ApiError(
                                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                                "上传文件不能超过10MB");

                return ResponseEntity
                                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                                .body(error);
        }

        @ExceptionHandler(DocumentParsingException.class)
        public ResponseEntity<ApiError> handleDocumentParsing(
                        DocumentParsingException exception) {

                ApiError error = new ApiError(
                                HttpStatus.UNPROCESSABLE_CONTENT.value(),
                                exception.getMessage());

                return ResponseEntity
                                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                                .body(error);
        }

        @ExceptionHandler(DocumentStorageException.class)
        public ResponseEntity<ApiError> handleDocumentStorage(
                        DocumentStorageException exception) {

                log.error("Document storage failed", exception);

                ApiError error = new ApiError(
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                "服务器无法保存上传文件");

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(error);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
                log.error("Unhandled request error", exception);
                ApiError error = new ApiError(
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                "服务暂时不可用，请查看后端日志");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }

        @ExceptionHandler(KnowledgeBaseNotFoundException.class)
        public ResponseEntity<ApiError> handleKnowledgeBaseNotFound(
                        KnowledgeBaseNotFoundException exception) {

                ApiError error = new ApiError(
                                HttpStatus.NOT_FOUND.value(),
                                exception.getMessage());

                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(error);
        }
}
