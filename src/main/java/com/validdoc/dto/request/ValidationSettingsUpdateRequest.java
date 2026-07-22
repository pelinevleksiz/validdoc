package com.validdoc.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ValidationSettingsUpdateRequest {

    @NotNull
    @Positive
    private Integer retentionDays;

    @NotNull
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double inkDensityThreshold;

    @NotNull
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private Double ocrConfidenceThreshold;

    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }

    public Double getInkDensityThreshold() { return inkDensityThreshold; }
    public void setInkDensityThreshold(Double inkDensityThreshold) { this.inkDensityThreshold = inkDensityThreshold; }

    public Double getOcrConfidenceThreshold() { return ocrConfidenceThreshold; }
    public void setOcrConfidenceThreshold(Double ocrConfidenceThreshold) { this.ocrConfidenceThreshold = ocrConfidenceThreshold; }
}