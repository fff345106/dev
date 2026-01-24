package com.example.hello.dto;

import jakarta.validation.constraints.NotNull;

public class AuditRequest {
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;  // true=通过, false=拒绝

    private String rejectReason;  // 拒绝原因（拒绝时必填）

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
