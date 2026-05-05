package com.example.hello.dto;

public class AppReviewStatsResponse {

    private Double averageRating;
    private Long totalReviews;

    public AppReviewStatsResponse() {}

    public AppReviewStatsResponse(Double averageRating, Long totalReviews) {
        this.averageRating = averageRating;
        this.totalReviews = totalReviews;
    }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }
    public Long getTotalReviews() { return totalReviews; }
    public void setTotalReviews(Long totalReviews) { this.totalReviews = totalReviews; }
}
