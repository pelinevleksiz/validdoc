package com.validdoc.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "validation")
public class ValidationProperties {

    private double confidenceThreshold = 0.80;
    private int retentionDays = 90;
    private double reviewMargin = 0.05;
    private double inkDensityThreshold = 0.015;
    private double weightCompleteness = 0.4;
    private double weightFormat = 0.4;
    private double weightSignature = 0.2;

    @PostConstruct
    public void validateWeights() {
        double sum = weightCompleteness + weightFormat + weightSignature;
        if (Math.abs(sum - 1.0) > 0.0001) {
            throw new IllegalStateException("validation.weight-* toplami 1.0 olmalidir, mevcut toplam: " + sum);
        }
    }

    public double getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(double confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }
    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    public double getReviewMargin() { return reviewMargin; }
    public void setReviewMargin(double reviewMargin) { this.reviewMargin = reviewMargin; }
    public double getInkDensityThreshold() { return inkDensityThreshold; }
    public void setInkDensityThreshold(double inkDensityThreshold) { this.inkDensityThreshold = inkDensityThreshold; }
    public double getWeightCompleteness() { return weightCompleteness; }
    public void setWeightCompleteness(double weightCompleteness) { this.weightCompleteness = weightCompleteness; }
    public double getWeightFormat() { return weightFormat; }
    public void setWeightFormat(double weightFormat) { this.weightFormat = weightFormat; }
    public double getWeightSignature() { return weightSignature; }
    public void setWeightSignature(double weightSignature) { this.weightSignature = weightSignature; }
}