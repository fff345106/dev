package com.example.hello.dto;

import java.util.List;

public class AuthorStatsResponse {
    private long totalViews;
    private long totalLikes;
    private long totalDownloads;
    private long totalFollowers;
    private long totalPatterns;
    private long totalArticles;
    private List<DailyTrendItem> dailyTrend;

    public AuthorStatsResponse() {}

    public long getTotalViews() { return totalViews; }
    public void setTotalViews(long totalViews) { this.totalViews = totalViews; }
    public long getTotalLikes() { return totalLikes; }
    public void setTotalLikes(long totalLikes) { this.totalLikes = totalLikes; }
    public long getTotalDownloads() { return totalDownloads; }
    public void setTotalDownloads(long totalDownloads) { this.totalDownloads = totalDownloads; }
    public long getTotalFollowers() { return totalFollowers; }
    public void setTotalFollowers(long totalFollowers) { this.totalFollowers = totalFollowers; }
    public long getTotalPatterns() { return totalPatterns; }
    public void setTotalPatterns(long totalPatterns) { this.totalPatterns = totalPatterns; }
    public long getTotalArticles() { return totalArticles; }
    public void setTotalArticles(long totalArticles) { this.totalArticles = totalArticles; }
    public List<DailyTrendItem> getDailyTrend() { return dailyTrend; }
    public void setDailyTrend(List<DailyTrendItem> dailyTrend) { this.dailyTrend = dailyTrend; }
}
