package com.validdoc.exception;

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] args;
    private final Long retryAfterSeconds;
    private final Long remainingAttempts;

    public ApiException(ErrorCode errorCode, Object... args) {
        this(errorCode, null, null, args);
    }

    public ApiException(ErrorCode errorCode, Long retryAfterSeconds, Object... args) {
        this(errorCode, retryAfterSeconds, null, args);
    }

    private ApiException(ErrorCode errorCode, Long retryAfterSeconds, Long remainingAttempts, Object... args) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.args = args;
        this.retryAfterSeconds = retryAfterSeconds;
        this.remainingAttempts = remainingAttempts;
    }

    public static ApiException withRemainingAttempts(ErrorCode errorCode, long remainingAttempts, Object... args) {
        return new ApiException(errorCode, null, remainingAttempts, args);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Long getRemainingAttempts() {
        return remainingAttempts;
    }
}