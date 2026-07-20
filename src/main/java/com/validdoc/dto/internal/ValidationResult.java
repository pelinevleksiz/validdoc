package com.validdoc.dto.internal;

import com.validdoc.model.enums.DocumentStatus;

public class ValidationResult {

    private final DocumentStatus status;
    private final String segmentResultsJson;

    public ValidationResult(DocumentStatus status, String segmentResultsJson) {
        this.status = status;
        this.segmentResultsJson = segmentResultsJson;
    }

    public DocumentStatus getStatus() { return status; }
    public String getSegmentResultsJson() { return segmentResultsJson; }
}