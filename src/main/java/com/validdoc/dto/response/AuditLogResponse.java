package com.validdoc.dto.response;

import java.time.Instant;

public class AuditLogResponse {

    private final Long id;
    private final Long documentId;
    private final String action;
    private final String performedBy;
    private final Long targetUserId;
    private final String details;
    private final Instant timestamp;

    public AuditLogResponse(Long id, Long documentId, String action, String performedBy, Long targetUserId, String details, Instant timestamp) {
        this.id = id;
        this.documentId = documentId;
        this.action = action;
        this.performedBy = performedBy;
        this.targetUserId = targetUserId;
        this.details = details;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public String getAction() { return action; }
    public String getPerformedBy() { return performedBy; }
    public Long getTargetUserId() { return targetUserId; }
    public String getDetails() { return details; }
    public Instant getTimestamp() { return timestamp; }
}