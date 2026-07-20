package com.validdoc.dto.response;

public class TemplatePreviewSegmentResponse {

    private final String label;
    private final int page;
    private final String rawText;
    private final double inkDensity;

    public TemplatePreviewSegmentResponse(String label, int page, String rawText, double inkDensity) {
        this.label = label;
        this.page = page;
        this.rawText = rawText;
        this.inkDensity = inkDensity;
    }

    public String getLabel() { return label; }
    public int getPage() { return page; }
    public String getRawText() { return rawText; }
    public double getInkDensity() { return inkDensity; }
}