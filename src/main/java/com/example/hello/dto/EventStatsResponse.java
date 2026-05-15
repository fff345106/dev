package com.example.hello.dto;

import java.util.List;

public class EventStatsResponse {
    private Long eventId;
    private String eventName;
    private Long viewCount;
    private Long participantCount;
    private Long submissionCount;
    private Long shareCount;
    private List<EventDailyTrendItem> dailyTrend;

    public EventStatsResponse() {
    }

    public EventStatsResponse(Long eventId, String eventName, Long viewCount, Long participantCount,
                              Long submissionCount, Long shareCount, List<EventDailyTrendItem> dailyTrend) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.viewCount = viewCount;
        this.participantCount = participantCount;
        this.submissionCount = submissionCount;
        this.shareCount = shareCount;
        this.dailyTrend = dailyTrend;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public Long getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(Long participantCount) {
        this.participantCount = participantCount;
    }

    public Long getSubmissionCount() {
        return submissionCount;
    }

    public void setSubmissionCount(Long submissionCount) {
        this.submissionCount = submissionCount;
    }

    public Long getShareCount() {
        return shareCount;
    }

    public void setShareCount(Long shareCount) {
        this.shareCount = shareCount;
    }

    public List<EventDailyTrendItem> getDailyTrend() {
        return dailyTrend;
    }

    public void setDailyTrend(List<EventDailyTrendItem> dailyTrend) {
        this.dailyTrend = dailyTrend;
    }
}
