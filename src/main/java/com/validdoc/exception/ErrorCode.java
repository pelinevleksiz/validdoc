package com.validdoc.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "error.file.too_large"),
    SERVER_BUSY(HttpStatus.TOO_MANY_REQUESTS, "error.server.busy"),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "error.auth.bad_credentials"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "error.access.denied"),
    DUPLICATE_RECORD(HttpStatus.CONFLICT, "error.record.duplicate"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "error.user.not_found"),
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.template.not_found"),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "error.document.not_found"),
    INVALID_DOCUMENT_STATUS(HttpStatus.BAD_REQUEST, "error.document.invalid_status"),
    INVALID_FIELD_DEFINITIONS(HttpStatus.BAD_REQUEST, "error.template.invalid_field_definitions"),
    INVALID_WEIGHTS_SUM(HttpStatus.BAD_REQUEST, "error.settings.weights_sum"),
    INTERNAL_UNEXPECTED(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal.unexpected");

    private final HttpStatus status;
    private final String messageKey;

    ErrorCode(HttpStatus status, String messageKey) {
        this.status = status;
        this.messageKey = messageKey;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessageKey() {
        return messageKey;
    }
}