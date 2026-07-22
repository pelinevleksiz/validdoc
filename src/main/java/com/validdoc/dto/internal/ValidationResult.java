package com.validdoc.dto.internal;

import com.validdoc.model.enums.DocumentStatus;

import java.util.List;

public class ValidationResult {

    private final DocumentStatus status;
    private final String segmentResultsJson;
    private final List<SegmentResultEntry> entries;

    public ValidationResult(DocumentStatus status, String segmentResultsJson, List<SegmentResultEntry> entries) {
        this.status = status;
        this.segmentResultsJson = segmentResultsJson;
        this.entries = entries;
    }

    public DocumentStatus getStatus() { return status; }
    public String getSegmentResultsJson() { return segmentResultsJson; }
    public List<SegmentResultEntry> getEntries() { return entries; }
}