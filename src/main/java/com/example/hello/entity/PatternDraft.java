package com.example.hello.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "pattern_drafts")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PatternDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "main_category", length = 2)
    private String mainCategory;

    @Column(name = "sub_category", length = 2)
    private String subCategory;

    @Column(length = 2)
    private String style;

    @Column(length = 2)
    private String region;

    @Column(length = 2)
    private String period;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "image_source_type", length = 20)
    private String imageSourceType;

    @Column(name = "story_text", columnDefinition = "TEXT")
    private String storyText; // 藏品故事-文字描述

    @Column(name = "story_image_url")
    private String storyImageUrl; // 藏品故事-图片 (支持PDF)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User user;

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
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getImageSourceType() { return imageSourceType; }
    public void setImageSourceType(String imageSourceType) { this.imageSourceType = imageSourceType; }
    public String getStoryText() { return storyText; }
    public void setStoryText(String storyText) { this.storyText = storyText; }
    public String getStoryImageUrl() { return storyImageUrl; }
    public void setStoryImageUrl(String storyImageUrl) { this.storyImageUrl = storyImageUrl; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
