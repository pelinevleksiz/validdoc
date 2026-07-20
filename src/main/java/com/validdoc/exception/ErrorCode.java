package com.validdoc.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "error.file.too_large"),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "error.file.unsupported_type"),
    SERVER_BUSY(HttpStatus.TOO_MANY_REQUESTS, "error.server.busy"),
    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "error.auth.too_many_attempts"),
    TOO_MANY_UPLOAD_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "error.document.too_many_uploads"),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "error.auth.bad_credentials"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "error.access.denied"),
    DUPLICATE_RECORD(HttpStatus.CONFLICT, "error.record.duplicate"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "error.user.not_found"),
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.template.not_found"),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "error.document.not_found"),
    INVALID_DOCUMENT_STATUS(HttpStatus.BAD_REQUEST, "error.document.invalid_status"),
    INVALID_SEGMENT_COORDINATES(HttpStatus.BAD_REQUEST, "error.template.invalid_segment_coordinates"),
    INVALID_SEGMENT_RULE_COMBINATION(HttpStatus.BAD_REQUEST, "error.template.invalid_rule_combination"),
    INVALID_RULE_PARAM(HttpStatus.BAD_REQUEST, "error.template.invalid_rule_param"),
    TEMPLATE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "error.document.template_id_required"),
    PREVIEW_FAILED(HttpStatus.BAD_REQUEST, "error.template.preview_failed"),
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