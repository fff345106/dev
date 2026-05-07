package com.example.hello.dto;

import java.time.LocalDateTime;

import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;

public class CertificationResponse {
    private Long id;
    private CertificationType certificationType;
    private CertificationStatus status;
    private Boolean realNameVerified;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CertificationType getCertificationType() { return certificationType; }
    public void setCertificationType(CertificationType certificationType) { this.certificationType = certificationType; }
    public CertificationStatus getStatus() { return status; }
    public void setStatus(CertificationStatus status) { this.status = status; }
    public Boolean getRealNameVerified() { return realNameVerified; }
    public void setRealNameVerified(Boolean realNameVerified) { this.realNameVerified = realNameVerified; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
