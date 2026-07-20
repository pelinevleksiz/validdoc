package com.validdoc.dto.internal;

import com.validdoc.model.TemplateSegment;
import com.validdoc.model.enums.SegmentRuleType;

public class SegmentReading {

    private final TemplateSegment segment;
    private final String extractedText;
    private final Double pixelDensity;

    public SegmentReading(TemplateSegment segment, String extractedText, Double pixelDensity) {
        this.segment = segment;
        this.extractedText = extractedText;
        this.pixelDensity = pixelDensity;
    }

    public TemplateSegment getSegment() { return segment; }
    public String getExtractedText() { return extractedText; }
    public Double getPixelDensity() { return pixelDensity; }

    public boolean isInkSegment() {
        return segment.getRules().stream().anyMatch(r ->
                r.getRuleType() == SegmentRuleType.SIGNATURE_INK
                        || r.getRuleType() == SegmentRuleType.STAMP_INK);
    }
}