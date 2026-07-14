package com.validdoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "validation")
public class ValidationProperties {

    private double confidenceThreshold = 0.80;
    private int retentionDays = 90;

    public ValidationProperties() {
    }

    public ValidationProperties(double confidenceThreshold, int retentionDays) {
        this.confidenceThreshold = confidenceThreshold;
        this.retentionDays = retentionDays;
    }

    public double getConfidenceThreshold() {
        return this.confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public int getRetentionDays() {
        return this.retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}