package com.example.hello.dto;

import com.example.hello.enums.CollaborationStatus;

import jakarta.validation.constraints.NotNull;

public class CollaborationUpdateRequest {

    @NotNull(message = "状态不能为空")
    private CollaborationStatus status;

    private String reply;

    public CollaborationStatus getStatus() { return status; }
    public void setStatus(CollaborationStatus status) { this.status = status; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
}
