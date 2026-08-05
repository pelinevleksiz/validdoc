package com.validdoc.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "error.file.too_large"),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "error.file.unsupported_type"),
    SERVER_BUSY(HttpStatus.TOO_MANY_REQUESTS, "error.server.busy"),
    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "error.auth.too_many_attempts"),
    TOO_MANY_UPLOAD_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "error.document.too_many_uploads"),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "error.auth.bad_credentials"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "error.access.denied"),
    DUPLICATE_RECORD(HttpStatus.CONFLICT, "error.record.duplicate"),
    TEMPLATE_NAME_TAKEN(HttpStatus.CONFLICT, "error.template.name_taken"),
    USERNAME_TAKEN(HttpStatus.CONFLICT, "error.user.username_taken"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "error.user.not_found"),
    CANNOT_DELETE_LAST_ADMIN(HttpStatus.CONFLICT, "error.user.cannot_delete_last_admin"),
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.template.not_found"),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "error.document.not_found"),
    DOCUMENT_NOT_PENDING_REVIEW(HttpStatus.CONFLICT, "error.document.not_pending_review"),
    SEGMENT_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.segment.image_not_found"),
    SEGMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "error.segment.not_found"),
    SEGMENT_ALREADY_RESOLVED(HttpStatus.CONFLICT, "error.segment.already_resolved"),
    SEGMENT_NOT_YET_RESOLVED(HttpStatus.CONFLICT, "error.segment.not_yet_resolved"),
    OVERRIDE_NOTE_REQUIRED(HttpStatus.BAD_REQUEST, "error.segment.override_note_required"),
    OVERRIDE_OUTCOME_UNCHANGED(HttpStatus.BAD_REQUEST, "error.segment.override_outcome_unchanged"),
    INVALID_SEGMENT_RESOLUTION_OUTCOME(HttpStatus.BAD_REQUEST, "error.segment.invalid_resolution_outcome"),
    INVALID_SEGMENT_COORDINATES(HttpStatus.BAD_REQUEST, "error.template.invalid_segment_coordinates"),
    SEGMENT_PAGE_OUT_OF_BOUNDS(HttpStatus.BAD_REQUEST, "error.template.segment_page_out_of_bounds"),
    INVALID_SEGMENT_RULE_COMBINATION(HttpStatus.BAD_REQUEST, "error.template.invalid_rule_combination"),
    INVALID_RULE_PARAM(HttpStatus.BAD_REQUEST, "error.template.invalid_rule_param"),
    TEMPLATE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "error.document.template_id_required"),
    PDF_UNREADABLE(HttpStatus.BAD_REQUEST, "error.document.pdf_unreadable"),
    PAGE_COUNT_MISMATCH(HttpStatus.BAD_REQUEST, "error.document.page_count_mismatch"),
    PREVIEW_FAILED(HttpStatus.BAD_REQUEST, "error.template.preview_failed"),
    MALFORMED_REQUEST_BODY(HttpStatus.BAD_REQUEST, "error.request.malformed_body"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.request.resource_not_found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "error.request.method_not_allowed"),
    MISSING_REQUEST_PARAMETER(HttpStatus.BAD_REQUEST, "error.request.missing_parameter"),
    INVALID_PARAMETER_TYPE(HttpStatus.BAD_REQUEST, "error.request.invalid_parameter_type"),
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