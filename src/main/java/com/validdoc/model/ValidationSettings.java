package com.validdoc.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "validation_settings")
public class ValidationSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false)
    private double confidenceThreshold;

    @Column(nullable = false)
    private double reviewMargin;

    @Column(nullable = false)
    private int retentionDays;

    @Column(nullable = false)
    private double inkDensityThreshold;

    @Column(nullable = false)
    private double weightCompleteness;

    @Column(nullable = false)
    private double weightFormat;

    @Column(nullable = false)
    private double weightSignature;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false, length = 50)
    private String updatedBy;

    public ValidationSettings() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public double getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(double confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public double getReviewMargin() { return reviewMargin; }
    public void setReviewMargin(double reviewMargin) { this.reviewMargin = reviewMargin; }

    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }

    public double getInkDensityThreshold() { return inkDensityThreshold; }
    public void setInkDensityThreshold(double inkDensityThreshold) { this.inkDensityThreshold = inkDensityThreshold; }

    public double getWeightCompleteness() { return weightCompleteness; }
    public void setWeightCompleteness(double weightCompleteness) { this.weightCompleteness = weightCompleteness; }

    public double getWeightFormat() { return weightFormat; }
    public void setWeightFormat(double weightFormat) { this.weightFormat = weightFormat; }

    public double getWeightSignature() { return weightSignature; }
    public void setWeightSignature(double weightSignature) { this.weightSignature = weightSignature; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}