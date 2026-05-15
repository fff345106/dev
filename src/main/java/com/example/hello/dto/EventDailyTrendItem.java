package com.example.hello.dto;

public class EventDailyTrendItem {
    private String date;
    private long views;
    private long participants;
    private long submissions;
    private long shares;

    public EventDailyTrendItem() {
    }

    public EventDailyTrendItem(String date, long views, long participants, long submissions, long shares) {
        this.date = date;
        this.views = views;
        this.participants = participants;
        this.submissions = submissions;
        this.shares = shares;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getViews() {
        return views;
    }

    public void setViews(long views) {
        this.views = views;
    }

    public long getParticipants() {
        return participants;
    }

    public void setParticipants(long participants) {
        this.participants = participants;
    }

    public long getSubmissions() {
        return submissions;
    }

    public void setSubmissions(long submissions) {
        this.submissions = submissions;
    }

    public long getShares() {
        return shares;
    }

    public void setShares(long shares) {
        this.shares = shares;
    }
}
