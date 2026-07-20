package com.validdoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "validation")
public class ValidationProperties {

    private int retentionDays = 90;
    private double inkDensityThreshold = 0.015;

    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    public double getInkDensityThreshold() { return inkDensityThreshold; }
    public void setInkDensityThreshold(double inkDensityThreshold) { this.inkDensityThreshold = inkDensityThreshold; }
}