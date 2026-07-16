package com.validdoc.dto.ocr;

public class OcrFieldResult {

    private String label;
    private FieldType type;
    private String extractedText;
    private Double pixelDensity;

    public OcrFieldResult() {
    }

    public OcrFieldResult(String label, FieldType type, String extractedText, Double pixelDensity) {
        this.label = label;
        this.type = type;
        this.extractedText = extractedText;
        this.pixelDensity = pixelDensity;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public FieldType getType() { return type; }
    public void setType(FieldType type) { this.type = type; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
    public Double getPixelDensity() { return pixelDensity; }
    public void setPixelDensity(Double pixelDensity) { this.pixelDensity = pixelDensity; }
}