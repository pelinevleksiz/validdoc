package com.validdoc.dto.internal;

import com.validdoc.model.TemplateSegment;
import com.validdoc.model.enums.SegmentRuleType;

public class SegmentReading {

    private final TemplateSegment segment;
    private final String extractedText;
    private final Double pixelDensity;
    private final Double ocrConfidence;
    private final byte[] croppedImage;

    public SegmentReading(TemplateSegment segment, String extractedText, Double pixelDensity,
                          Double ocrConfidence, byte[] croppedImage) {
        this.segment = segment;
        this.extractedText = extractedText;
        this.pixelDensity = pixelDensity;
        this.ocrConfidence = ocrConfidence;
        this.croppedImage = croppedImage;
    }

    public TemplateSegment getSegment() { return segment; }
    public String getExtractedText() { return extractedText; }
    public Double getPixelDensity() { return pixelDensity; }
    public Double getOcrConfidence() { return ocrConfidence; }
    public byte[] getCroppedImage() { return croppedImage; }

    public boolean isInkSegment() {
        return segment.getRules().stream().anyMatch(r ->
                r.getRuleType() == SegmentRuleType.SIGNATURE_INK
                        || r.getRuleType() == SegmentRuleType.STAMP_INK);
    }
}