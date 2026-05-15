package com.example.hello.dto;

import java.util.List;

public class NotificationListResponse {
    private List<NotificationResponse> notifications;
    private long unreadCount;
    private int totalPages;
    private long totalElements;

    public NotificationListResponse() {}

    // Getters and Setters
    public List<NotificationResponse> getNotifications() { return notifications; }
    public void setNotifications(List<NotificationResponse> notifications) { this.notifications = notifications; }
    public long getUnreadCount() { return unreadCount; }
    public void setUnreadCount(long unreadCount) { this.unreadCount = unreadCount; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
}
