package com.validdoc.dto.internal;

import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.model.enums.ValidationMode;

public class ValidationResult {

    private final DocumentStatus status;
    private final ValidationMode validationMode;
    private final double confidenceScore;
    private final String validationErrorLogs;
    private final String extractedMaskedData;

    public ValidationResult(DocumentStatus status, ValidationMode validationMode, double confidenceScore,
                            String validationErrorLogs, String extractedMaskedData) {
        this.status = status;
        this.validationMode = validationMode;
        this.confidenceScore = confidenceScore;
        this.validationErrorLogs = validationErrorLogs;
        this.extractedMaskedData = extractedMaskedData;
    }

    public DocumentStatus getStatus() { return status; }
    public ValidationMode getValidationMode() { return validationMode; }
    public double getConfidenceScore() { return confidenceScore; }
    public String getValidationErrorLogs() { return validationErrorLogs; }
    public String getExtractedMaskedData() { return extractedMaskedData; }
}