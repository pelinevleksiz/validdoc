package com.validdoc.dto.response;

import com.validdoc.model.enums.DocumentLanguage;
import com.validdoc.model.enums.DocumentStatus;

import java.time.Instant;

public class DocumentSummaryResponse {

    private Long id;
    private String fileName;
    private DocumentStatus status;
    private Long templateId;
    private DocumentLanguage language;
    private String segmentResults;
    private String uploadedByUsername;
    private String operatorUsername;
    private Instant uploadedAt;
    private Instant processedAt;
    private String failureReason;

    public DocumentSummaryResponse() {}

    public DocumentSummaryResponse(Long id, String fileName, DocumentStatus status, Long templateId,
                                   DocumentLanguage language, String segmentResults,
                                   String uploadedByUsername, String operatorUsername,
                                   Instant uploadedAt, Instant processedAt, String failureReason) {
        this.id = id;
        this.fileName = fileName;
        this.status = status;
        this.templateId = templateId;
        this.language = language;
        this.segmentResults = segmentResults;
        this.uploadedByUsername = uploadedByUsername;
        this.operatorUsername = operatorUsername;
        this.uploadedAt = uploadedAt;
        this.processedAt = processedAt;
        this.failureReason = failureReason;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public DocumentLanguage getLanguage() { return language; }
    public void setLanguage(DocumentLanguage language) { this.language = language; }

    public String getSegmentResults() { return segmentResults; }
    public void setSegmentResults(String segmentResults) { this.segmentResults = segmentResults; }

    public String getUploadedByUsername() { return uploadedByUsername; }
    public void setUploadedByUsername(String uploadedByUsername) { this.uploadedByUsername = uploadedByUsername; }

    public String getOperatorUsername() { return operatorUsername; }
    public void setOperatorUsername(String operatorUsername) { this.operatorUsername = operatorUsername; }

    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}