package com.example.hello.dto;

import jakarta.validation.constraints.NotBlank;

public class CertificationAuditRequest {
    @NotBlank(message = "审核结果不能为空")
    private Boolean approved;

    private String rejectReason;

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
