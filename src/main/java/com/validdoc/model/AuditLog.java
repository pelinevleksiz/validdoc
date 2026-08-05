package com.validdoc.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;

@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id")
    private Long documentId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false, length = 50)
    private String performedBy;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    public AuditLog() {}

    public AuditLog(String action, String performedBy) {
        this(null, action, performedBy, null, null);
    }

    public AuditLog(Long documentId, String action, String performedBy) {
        this(documentId, action, performedBy, null, null);
    }

    public AuditLog(String action, String performedBy, Long targetUserId) {
        this(null, action, performedBy, targetUserId, null);
    }

    public AuditLog(Long documentId, String action, String performedBy, String details) {
        this(documentId, action, performedBy, null, details);
    }

    private AuditLog(Long documentId, String action, String performedBy, Long targetUserId, String details) {
        this.documentId = documentId;
        this.action = action;
        this.performedBy = performedBy;
        this.targetUserId = targetUserId;
        this.details = details;
        this.timestamp = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}