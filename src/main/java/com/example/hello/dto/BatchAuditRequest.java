package com.example.hello.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class BatchAuditRequest {
    @NotEmpty(message = "ID列表不能为空")
    private List<Long> ids;

    @NotNull(message = "审核结果不能为空")
    private Boolean approved;  // true=通过, false=拒绝

    private String rejectReason;  // 拒绝原因（拒绝时必填）

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
