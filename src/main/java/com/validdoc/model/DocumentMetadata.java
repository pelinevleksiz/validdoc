package com.validdoc.model;

import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.model.enums.DocumentLanguage;
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
    @Column(length = 10)
    private DocumentLanguage language = DocumentLanguage.TUR;

    @ManyToOne
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Convert(converter = MaskedDataEncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String segmentResults;

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

    public DocumentLanguage getLanguage() { return language; }
    public void setLanguage(DocumentLanguage language) { this.language = language; }

    public Template getTemplate() { return template; }
    public void setTemplate(Template template) { this.template = template; }

    public String getSegmentResults() { return segmentResults; }
    public void setSegmentResults(String segmentResults) { this.segmentResults = segmentResults; }

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