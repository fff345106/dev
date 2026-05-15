package com.example.hello.dto;

import java.time.LocalDateTime;

import com.example.hello.entity.Notification;

public class NotificationResponse {
    private Long id;
    private String type;
    private String content;
    private Long senderId;
    private String senderName;
    private String targetType;
    private Long targetId;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public NotificationResponse() {}

    public static NotificationResponse fromEntity(Notification n) {
        NotificationResponse response = new NotificationResponse();
        response.setId(n.getId());
        response.setType(n.getType() != null ? n.getType().name() : null);
        response.setContent(n.getContent());
        if (n.getSender() != null) {
            response.setSenderId(n.getSender().getId());
            response.setSenderName(n.getSender().getUsername());
        }
        response.setTargetType(n.getTargetType() != null ? n.getTargetType().name() : null);
        response.setTargetId(n.getTargetId());
        response.setIsRead(n.getIsRead());
        response.setCreatedAt(n.getCreatedAt());
        return response;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
