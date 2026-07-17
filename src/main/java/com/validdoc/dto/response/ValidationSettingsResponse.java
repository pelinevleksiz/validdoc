package com.validdoc.dto.response;

import java.time.LocalDateTime;

public class ValidationSettingsResponse {

    private final double confidenceThreshold;
    private final double reviewMargin;
    private final int retentionDays;
    private final double inkDensityThreshold;
    private final double weightCompleteness;
    private final double weightFormat;
    private final double weightSignature;
    private final LocalDateTime updatedAt;
    private final String updatedBy;

    public ValidationSettingsResponse(double confidenceThreshold, double reviewMargin, int retentionDays,
                                      double inkDensityThreshold, double weightCompleteness,
                                      double weightFormat, double weightSignature,
                                      LocalDateTime updatedAt, String updatedBy) {
        this.confidenceThreshold = confidenceThreshold;
        this.reviewMargin = reviewMargin;
        this.retentionDays = retentionDays;
        this.inkDensityThreshold = inkDensityThreshold;
        this.weightCompleteness = weightCompleteness;
        this.weightFormat = weightFormat;
        this.weightSignature = weightSignature;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public double getConfidenceThreshold() { return confidenceThreshold; }
    public double getReviewMargin() { return reviewMargin; }
    public int getRetentionDays() { return retentionDays; }
    public double getInkDensityThreshold() { return inkDensityThreshold; }
    public double getWeightCompleteness() { return weightCompleteness; }
    public double getWeightFormat() { return weightFormat; }
    public double getWeightSignature() { return weightSignature; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}