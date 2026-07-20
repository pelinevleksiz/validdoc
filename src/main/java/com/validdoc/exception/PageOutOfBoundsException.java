package com.validdoc.exception;

public class PageOutOfBoundsException extends RuntimeException {
    public PageOutOfBoundsException(String message, Throwable cause) {
        super(message, cause);
    }
}