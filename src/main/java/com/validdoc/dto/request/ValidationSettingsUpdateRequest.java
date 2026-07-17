package com.validdoc.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ValidationSettingsUpdateRequest {

    @NotNull
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double confidenceThreshold;

    @NotNull
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double reviewMargin;

    @NotNull
    @Positive
    private Integer retentionDays;

    @NotNull
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double inkDensityThreshold;

    @NotNull
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double weightCompleteness;

    @NotNull
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double weightFormat;

    @NotNull
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double weightSignature;

    public Double getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(Double confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public Double getReviewMargin() { return reviewMargin; }
    public void setReviewMargin(Double reviewMargin) { this.reviewMargin = reviewMargin; }

    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }

    public Double getInkDensityThreshold() { return inkDensityThreshold; }
    public void setInkDensityThreshold(Double inkDensityThreshold) { this.inkDensityThreshold = inkDensityThreshold; }

    public Double getWeightCompleteness() { return weightCompleteness; }
    public void setWeightCompleteness(Double weightCompleteness) { this.weightCompleteness = weightCompleteness; }

    public Double getWeightFormat() { return weightFormat; }
    public void setWeightFormat(Double weightFormat) { this.weightFormat = weightFormat; }

    public Double getWeightSignature() { return weightSignature; }
    public void setWeightSignature(Double weightSignature) { this.weightSignature = weightSignature; }
}