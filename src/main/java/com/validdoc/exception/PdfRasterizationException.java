package com.validdoc.exception;

/** PDF bozuk, sifreli veya rasterize edilemez oldugunda firlatilir (SDD S8). */
public class PdfRasterizationException extends RuntimeException {
    public PdfRasterizationException(String message, Throwable cause) {
        super(message, cause);
    }
}