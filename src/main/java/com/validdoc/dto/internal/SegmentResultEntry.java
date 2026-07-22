package com.validdoc.dto.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.validdoc.model.enums.SegmentOutcome;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SegmentResultEntry {

    private Long segmentId;
    private String label;
    private SegmentOutcome outcome;
    private List<String> failedRules;
    private String maskedValue;
    private Double ocrConfidence;
    private boolean manuallyResolved;
    private String resolvedBy;
    private LocalDateTime resolvedAt;

    public SegmentResultEntry() {}

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public SegmentOutcome getOutcome() { return outcome; }
    public void setOutcome(SegmentOutcome outcome) { this.outcome = outcome; }

    public List<String> getFailedRules() { return failedRules; }
    public void setFailedRules(List<String> failedRules) { this.failedRules = failedRules; }

    public String getMaskedValue() { return maskedValue; }
    public void setMaskedValue(String maskedValue) { this.maskedValue = maskedValue; }

    public Double getOcrConfidence() { return ocrConfidence; }
    public void setOcrConfidence(Double ocrConfidence) { this.ocrConfidence = ocrConfidence; }

    public boolean isManuallyResolved() { return manuallyResolved; }
    public void setManuallyResolved(boolean manuallyResolved) { this.manuallyResolved = manuallyResolved; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}