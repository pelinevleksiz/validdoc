package com.validdoc.exception;

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] args;

    public ApiException(ErrorCode errorCode, Object... args) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.args = args;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args;
    }
}