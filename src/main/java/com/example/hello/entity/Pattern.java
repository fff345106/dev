package com.example.hello.entity;



import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "patterns")
public class Pattern {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String description;  // 纹样描述

    @Column(name = "main_category", length = 2, nullable = false)
    private String mainCategory;  // 主类别代码: AN/PL/PE/LA/AB/OR/SY/CE/MY/OT

    @Column(name = "sub_category", length = 2, nullable = false)
    private String subCategory;  // 子类别代码（无子类别时为风格1）

    @Column(length = 2, nullable = false)
    private String style;  // 风格代码（无子类别时为风格2）

    @Column(length = 2, nullable = false)
    private String region;  // 地区代码

    @Column(length = 2, nullable = false)
    private String period;  // 时期代码: XS/QG/WS/TG/SG/MG/MJ/XD/OT

    @Column(name = "date_code", length = 6, nullable = false)
    private String dateCode;  // 日期代码: YYMMDD格式

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;  // 序列号: 当日上传序列号

    @Column(name = "pattern_code", unique = true, nullable = false)
    private String patternCode;  // 纹样编码: 如 AN-BD-TR-CN-QG-240615-001

    @Column(name = "image_url")
    private String imageUrl;  // 纹样图片URL

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

    // Getters and Setters
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
    public String getDateCode() { return dateCode; }
    public void setDateCode(String dateCode) { this.dateCode = dateCode; }
    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public String getPatternCode() { return patternCode; }
    public void setPatternCode(String patternCode) { this.patternCode = patternCode; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
