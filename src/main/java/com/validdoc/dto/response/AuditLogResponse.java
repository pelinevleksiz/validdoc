package com.validdoc.dto.response;

import java.time.Instant;

public class AuditLogResponse {

    private final Long id;
    private final Long documentId;
    private final String action;
    private final String performedBy;
    private final Instant timestamp;

    public AuditLogResponse(Long id, Long documentId, String action, String performedBy, Instant timestamp) {
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
    public Instant getTimestamp() { return timestamp; }
}