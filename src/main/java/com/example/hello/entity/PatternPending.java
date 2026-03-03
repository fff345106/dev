package com.example.hello.entity;

import java.time.LocalDateTime;

import com.example.hello.enums.AuditStatus;
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
@Table(name = "patterns_pending")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PatternPending {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "main_category", length = 2, nullable = false)
    private String mainCategory;

    @Column(name = "sub_category", length = 2, nullable = false)
    private String subCategory;

    @Column(length = 2, nullable = false)
    private String style;

    @Column(length = 2, nullable = false)
    private String region;

    @Column(length = 2, nullable = false)
    private String period;

    @Column(name = "image_url")
    private String imageUrl;

    // 编码相关字段（提交时生成）
    @Column(name = "date_code", length = 6)
    private String dateCode;

    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    @Column(name = "pattern_code", unique = true)
    private String patternCode;

    // 审核相关字段
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditStatus status = AuditStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitter_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User submitter;  // 提交人

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auditor_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User auditor;  // 审核人

    @Column(name = "audit_time")
    private LocalDateTime auditTime;  // 审核时间

    @Column(name = "reject_reason")
    private String rejectReason;  // 拒绝原因

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getter 和 Setter 方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMainCategory() { return mainCategory; }
    public void setMainCategory(String mainCategory) { this.mainCategory = mainCategory; }
    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public AuditStatus getStatus() { return status; }
    public void setStatus(AuditStatus status) { this.status = status; }
    public User getSubmitter() { return submitter; }
    public void setSubmitter(User submitter) { this.submitter = submitter; }
    public User getAuditor() { return auditor; }
    public void setAuditor(User auditor) { this.auditor = auditor; }
    public LocalDateTime getAuditTime() { return auditTime; }
    public void setAuditTime(LocalDateTime auditTime) { this.auditTime = auditTime; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getDateCode() { return dateCode; }
    public void setDateCode(String dateCode) { this.dateCode = dateCode; }
    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public String getPatternCode() { return patternCode; }
    public void setPatternCode(String patternCode) { this.patternCode = patternCode; }
}
