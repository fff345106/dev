package com.example.hello.entity;

import java.time.LocalDateTime;

import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_certifications")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class UserCertification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "certification_type", nullable = false)
    private CertificationType certificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificationStatus status = CertificationStatus.PENDING;

    // 实名认证字段
    @Column(name = "real_name", length = 50)
    private String realName;

    @Column(name = "id_card_number", length = 18)
    private String idCardNumber;

    @Column(name = "id_card_front_url", length = 500)
    private String idCardFrontUrl;

    @Column(name = "id_card_back_url", length = 500)
    private String idCardBackUrl;

    @Column(name = "real_name_verified")
    private Boolean realNameVerified = false;

    // 企业认证字段
    @Column(name = "business_license_url", length = 500)
    private String businessLicenseUrl;

    @Column(name = "authorization_letter_url", length = 500)
    private String authorizationLetterUrl;

    @Column(name = "legal_representative_name", length = 50)
    private String legalRepresentativeName;

    @Column(name = "is_legal_representative")
    private Boolean isLegalRepresentative;

    // 技艺认证字段
    @Column(name = "certification_url", length = 500)
    private String certificationUrl;

    @Column(name = "representative_work_url", length = 500)
    private String representativeWorkUrl;

    // 审核信息
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "auditor_id")
    private Long auditorId;

    @Column(name = "audit_time")
    private LocalDateTime auditTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserCertification() {}

    public UserCertification(User user, CertificationType certificationType) {
        this.user = user;
        this.certificationType = certificationType;
        this.status = CertificationStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public CertificationType getCertificationType() { return certificationType; }
    public void setCertificationType(CertificationType certificationType) { this.certificationType = certificationType; }
    public CertificationStatus getStatus() { return status; }
    public void setStatus(CertificationStatus status) { this.status = status; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getIdCardNumber() { return idCardNumber; }
    public void setIdCardNumber(String idCardNumber) { this.idCardNumber = idCardNumber; }
    public String getIdCardFrontUrl() { return idCardFrontUrl; }
    public void setIdCardFrontUrl(String idCardFrontUrl) { this.idCardFrontUrl = idCardFrontUrl; }
    public String getIdCardBackUrl() { return idCardBackUrl; }
    public void setIdCardBackUrl(String idCardBackUrl) { this.idCardBackUrl = idCardBackUrl; }
    public Boolean getRealNameVerified() { return realNameVerified; }
    public void setRealNameVerified(Boolean realNameVerified) { this.realNameVerified = realNameVerified; }
    public String getBusinessLicenseUrl() { return businessLicenseUrl; }
    public void setBusinessLicenseUrl(String businessLicenseUrl) { this.businessLicenseUrl = businessLicenseUrl; }
    public String getAuthorizationLetterUrl() { return authorizationLetterUrl; }
    public void setAuthorizationLetterUrl(String authorizationLetterUrl) { this.authorizationLetterUrl = authorizationLetterUrl; }
    public String getLegalRepresentativeName() { return legalRepresentativeName; }
    public void setLegalRepresentativeName(String legalRepresentativeName) { this.legalRepresentativeName = legalRepresentativeName; }
    public Boolean getIsLegalRepresentative() { return isLegalRepresentative; }
    public void setIsLegalRepresentative(Boolean isLegalRepresentative) { this.isLegalRepresentative = isLegalRepresentative; }
    public String getCertificationUrl() { return certificationUrl; }
    public void setCertificationUrl(String certificationUrl) { this.certificationUrl = certificationUrl; }
    public String getRepresentativeWorkUrl() { return representativeWorkUrl; }
    public void setRepresentativeWorkUrl(String representativeWorkUrl) { this.representativeWorkUrl = representativeWorkUrl; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Long getAuditorId() { return auditorId; }
    public void setAuditorId(Long auditorId) { this.auditorId = auditorId; }
    public LocalDateTime getAuditTime() { return auditTime; }
    public void setAuditTime(LocalDateTime auditTime) { this.auditTime = auditTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
