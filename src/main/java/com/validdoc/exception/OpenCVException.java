package com.validdoc.exception;

public class OpenCVException extends RuntimeException {
    public OpenCVException(String message) {
        super(message);
    }
    public OpenCVException(String message, Throwable cause) {
        super(message, cause);
    }
}