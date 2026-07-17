package com.validdoc.model.enums;

public enum DocumentLanguage {

    TUR("tur"),
    ENG("eng");

    private final String tesseractCode;

    DocumentLanguage(String tesseractCode) {
        this.tesseractCode = tesseractCode;
    }

    public String getTesseractCode() {
        return tesseractCode;
    }

    public static DocumentLanguage fromParam(String raw) {
        if (raw != null && raw.trim().equalsIgnoreCase("eng")) {
            return ENG;
        }
        return TUR;
    }
}