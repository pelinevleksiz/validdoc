package com.validdoc.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(String code, String message, Long retryAfterSeconds, Long remainingAttempts) {

    public ApiErrorResponse(String code, String message) {
        this(code, message, null, null);
    }

    public ApiErrorResponse(String code, String message, Long retryAfterSeconds) {
        this(code, message, retryAfterSeconds, null);
    }
}