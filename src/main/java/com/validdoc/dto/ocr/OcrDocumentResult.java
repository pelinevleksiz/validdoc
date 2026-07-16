package com.validdoc.dto.ocr;

import java.util.ArrayList;
import java.util.List;

public class OcrDocumentResult {

    private String rawFullText;
    private List<OcrFieldResult> fields = new ArrayList<>();

    public OcrDocumentResult() {
    }

    public OcrDocumentResult(String rawFullText, List<OcrFieldResult> fields) {
        this.rawFullText = rawFullText;
        this.fields = fields;
    }

    public String getRawFullText() { return rawFullText; }
    public void setRawFullText(String rawFullText) { this.rawFullText = rawFullText; }
    public List<OcrFieldResult> getFields() { return fields; }
    public void setFields(List<OcrFieldResult> fields) { this.fields = fields; }
}