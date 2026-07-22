package com.validdoc.dto.response;

import java.time.LocalDateTime;

public class ValidationSettingsResponse {

    private final int retentionDays;
    private final double inkDensityThreshold;
    private final double ocrConfidenceThreshold;
    private final LocalDateTime updatedAt;
    private final String updatedBy;

    public ValidationSettingsResponse(int retentionDays, double inkDensityThreshold, double ocrConfidenceThreshold,
                                      LocalDateTime updatedAt, String updatedBy) {
        this.retentionDays = retentionDays;
        this.inkDensityThreshold = inkDensityThreshold;
        this.ocrConfidenceThreshold = ocrConfidenceThreshold;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public int getRetentionDays() { return retentionDays; }
    public double getInkDensityThreshold() { return inkDensityThreshold; }
    public double getOcrConfidenceThreshold() { return ocrConfidenceThreshold; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}