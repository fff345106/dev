package com.example.hello.dto;

import jakarta.validation.constraints.NotNull;

public class CollaborationCreateRequest {

    @NotNull(message = "大师ID不能为空")
    private Long masterId;

    private String message;

    public Long getMasterId() { return masterId; }
    public void setMasterId(Long masterId) { this.masterId = masterId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
