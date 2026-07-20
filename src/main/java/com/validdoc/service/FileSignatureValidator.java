package com.validdoc.service;

public final class FileSignatureValidator {

    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46, 0x2D};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private FileSignatureValidator() {}

    public static String detectContentType(byte[] fileBytes) {
        if (matches(fileBytes, PDF_MAGIC)) {
            return "application/pdf";
        }
        if (matches(fileBytes, PNG_MAGIC)) {
            return "image/png";
        }
        if (matches(fileBytes, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        return null;
    }

    private static boolean matches(byte[] data, byte[] magic) {
        if (data == null || data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}