package com.example.hello.dto;

public class DailyTrendItem {
    private String date;
    private long views;
    private long likes;
    private long downloads;

    public DailyTrendItem() {}

    public DailyTrendItem(String date, long views, long likes, long downloads) {
        this.date = date;
        this.views = views;
        this.likes = likes;
        this.downloads = downloads;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public long getViews() { return views; }
    public void setViews(long views) { this.views = views; }
    public long getLikes() { return likes; }
    public void setLikes(long likes) { this.likes = likes; }
    public long getDownloads() { return downloads; }
    public void setDownloads(long downloads) { this.downloads = downloads; }
}
