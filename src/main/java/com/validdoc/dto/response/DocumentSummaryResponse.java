package com.validdoc.dto.response;

import com.validdoc.model.enums.DocumentLanguage;
import com.validdoc.model.enums.DocumentStatus;

import java.time.LocalDateTime;

public class DocumentSummaryResponse {

    private Long id;
    private DocumentStatus status;
    private Long templateId;
    private DocumentLanguage language;
    private String segmentResults;
    private LocalDateTime uploadedAt;
    private LocalDateTime processedAt;

    public DocumentSummaryResponse() {}

    public DocumentSummaryResponse(Long id, DocumentStatus status, Long templateId,
                                   DocumentLanguage language, String segmentResults,
                                   LocalDateTime uploadedAt, LocalDateTime processedAt) {
        this.id = id;
        this.status = status;
        this.templateId = templateId;
        this.language = language;
        this.segmentResults = segmentResults;
        this.uploadedAt = uploadedAt;
        this.processedAt = processedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public DocumentLanguage getLanguage() { return language; }
    public void setLanguage(DocumentLanguage language) { this.language = language; }

    public String getSegmentResults() { return segmentResults; }
    public void setSegmentResults(String segmentResults) { this.segmentResults = segmentResults; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}