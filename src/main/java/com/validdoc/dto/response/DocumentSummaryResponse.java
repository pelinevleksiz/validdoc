package com.validdoc.dto.response;

import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.model.enums.ValidationMode;

import java.time.LocalDateTime;

public class DocumentSummaryResponse {

    private Long id;
    private DocumentStatus status;
    private ValidationMode validationMode;
    private Long templateId;
    private Double confidenceScore;
    private String validationErrorLogs;
    private String extractedMaskedData;
    private LocalDateTime uploadedAt;
    private LocalDateTime processedAt;

    public DocumentSummaryResponse() {}

    public DocumentSummaryResponse(Long id, DocumentStatus status, ValidationMode validationMode, Long templateId,
                                   Double confidenceScore, String validationErrorLogs, String extractedMaskedData,
                                   LocalDateTime uploadedAt, LocalDateTime processedAt) {
        this.id = id;
        this.status = status;
        this.validationMode = validationMode;
        this.templateId = templateId;
        this.confidenceScore = confidenceScore;
        this.validationErrorLogs = validationErrorLogs;
        this.extractedMaskedData = extractedMaskedData;
        this.uploadedAt = uploadedAt;
        this.processedAt = processedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public ValidationMode getValidationMode() { return validationMode; }
    public void setValidationMode(ValidationMode validationMode) { this.validationMode = validationMode; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getValidationErrorLogs() { return validationErrorLogs; }
    public void setValidationErrorLogs(String validationErrorLogs) { this.validationErrorLogs = validationErrorLogs; }

    public String getExtractedMaskedData() { return extractedMaskedData; }
    public void setExtractedMaskedData(String extractedMaskedData) { this.extractedMaskedData = extractedMaskedData; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}