package com.example.hello.dto;

import java.time.LocalDateTime;

import com.example.hello.entity.Collaboration;

public class CollaborationResponse {

    private Long id;
    private Long enterpriseId;
    private String enterpriseName;
    private Long masterId;
    private String masterName;
    private String status;
    private String message;
    private String reply;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CollaborationResponse fromEntity(Collaboration c) {
        CollaborationResponse response = new CollaborationResponse();
        response.setId(c.getId());
        if (c.getEnterprise() != null) {
            response.setEnterpriseId(c.getEnterprise().getId());
            response.setEnterpriseName(c.getEnterprise().getUsername());
        }
        if (c.getMaster() != null) {
            response.setMasterId(c.getMaster().getId());
            response.setMasterName(c.getMaster().getUsername());
        }
        response.setStatus(c.getStatus().name());
        response.setMessage(c.getMessage());
        response.setReply(c.getReply());
        response.setCreatedAt(c.getCreatedAt());
        response.setUpdatedAt(c.getUpdatedAt());
        return response;
    }

    // Getter 和 Setter 方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long enterpriseId) { this.enterpriseId = enterpriseId; }
    public String getEnterpriseName() { return enterpriseName; }
    public void setEnterpriseName(String enterpriseName) { this.enterpriseName = enterpriseName; }
    public Long getMasterId() { return masterId; }
    public void setMasterId(Long masterId) { this.masterId = masterId; }
    public String getMasterName() { return masterName; }
    public void setMasterName(String masterName) { this.masterName = masterName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
