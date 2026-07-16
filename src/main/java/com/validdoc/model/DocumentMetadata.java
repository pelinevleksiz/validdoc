package com.validdoc.model;

import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.model.enums.ValidationMode;
import com.validdoc.security.MaskedDataEncryptionConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_metadata")
public class DocumentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentStatus status = DocumentStatus.PROCESSING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ValidationMode validationMode;

    @ManyToOne
    @JoinColumn(name = "template_id")
    private Template template;

    private Double confidenceScore;

    @Column(columnDefinition = "TEXT")
    private String validationErrorLogs;

    @Convert(converter = MaskedDataEncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String extractedMaskedData;

    @ManyToOne
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    private LocalDateTime processedAt;

    private LocalDateTime purgeAt;

    @ManyToOne
    @JoinColumn(name = "operator_id")
    private User operator;

    public DocumentMetadata() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public ValidationMode getValidationMode() { return validationMode; }
    public void setValidationMode(ValidationMode validationMode) { this.validationMode = validationMode; }

    public Template getTemplate() { return template; }
    public void setTemplate(Template template) { this.template = template; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getValidationErrorLogs() { return validationErrorLogs; }
    public void setValidationErrorLogs(String validationErrorLogs) { this.validationErrorLogs = validationErrorLogs; }

    public String getExtractedMaskedData() { return extractedMaskedData; }
    public void setExtractedMaskedData(String extractedMaskedData) { this.extractedMaskedData = extractedMaskedData; }

    public User getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(User uploadedBy) { this.uploadedBy = uploadedBy; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public LocalDateTime getPurgeAt() { return purgeAt; }
    public void setPurgeAt(LocalDateTime purgeAt) { this.purgeAt = purgeAt; }

    public User getOperator() { return operator; }
    public void setOperator(User operator) { this.operator = operator; }
}