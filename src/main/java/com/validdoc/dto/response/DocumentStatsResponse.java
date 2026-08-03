package com.validdoc.dto.response;

public class DocumentStatsResponse {

    private long todayUploads;
    private long pendingReview;
    private Double weeklyValidationRate;

    public DocumentStatsResponse() {}

    public DocumentStatsResponse(long todayUploads, long pendingReview, Double weeklyValidationRate) {
        this.todayUploads = todayUploads;
        this.pendingReview = pendingReview;
        this.weeklyValidationRate = weeklyValidationRate;
    }

    public long getTodayUploads() { return todayUploads; }
    public void setTodayUploads(long todayUploads) { this.todayUploads = todayUploads; }

    public long getPendingReview() { return pendingReview; }
    public void setPendingReview(long pendingReview) { this.pendingReview = pendingReview; }

    public Double getWeeklyValidationRate() { return weeklyValidationRate; }
    public void setWeeklyValidationRate(Double weeklyValidationRate) { this.weeklyValidationRate = weeklyValidationRate; }
}