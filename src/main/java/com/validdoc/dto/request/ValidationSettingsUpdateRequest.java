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

    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }

    public Double getInkDensityThreshold() { return inkDensityThreshold; }
    public void setInkDensityThreshold(Double inkDensityThreshold) { this.inkDensityThreshold = inkDensityThreshold; }
}