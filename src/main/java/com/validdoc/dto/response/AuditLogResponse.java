package com.validdoc.dto.response;

import java.time.LocalDateTime;

public class AuditLogResponse {

    private final Long id;
    private final Long documentId;
    private final String action;
    private final String performedBy;
    private final LocalDateTime timestamp;

    public AuditLogResponse(Long id, Long documentId, String action, String performedBy, LocalDateTime timestamp) {
        this.id = id;
        this.documentId = documentId;
        this.action = action;
        this.performedBy = performedBy;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public String getAction() { return action; }
    public String getPerformedBy() { return performedBy; }
    public LocalDateTime getTimestamp() { return timestamp; }
}