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
    private int retentionDays;

    @Column(nullable = false)
    private double inkDensityThreshold;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false, length = 50)
    private String updatedBy;

    public ValidationSettings() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }

    public double getInkDensityThreshold() { return inkDensityThreshold; }
    public void setInkDensityThreshold(double inkDensityThreshold) { this.inkDensityThreshold = inkDensityThreshold; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}