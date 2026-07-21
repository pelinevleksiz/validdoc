package com.validdoc.exception;

public class OcrEngineException extends RuntimeException {
    public OcrEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}