package com.validdoc.dto.request;

import com.validdoc.model.enums.DocumentStatus;
import jakarta.validation.constraints.NotNull;

public class VerificationRequest {

    @NotNull
    private DocumentStatus status;

    public VerificationRequest() {}

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }
}