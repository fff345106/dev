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
@Table(name = "digital_collectibles")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class DigitalCollectible {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_mode", length = 20, nullable = false)
    private String entryMode;

    @Column(name = "pattern_image_url", nullable = false)
    private String patternImageUrl;

    @Column(name = "pattern_image_source_type", length = 20, nullable = false)
    private String patternImageSourceType;

    @Column(name = "source_pattern_id")
    private Long sourcePatternId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "story_text", columnDefinition = "TEXT")
    private String storyText;

    @Column(name = "story_file_url")
    private String storyFileUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEntryMode() {
        return entryMode;
    }

    public void setEntryMode(String entryMode) {
        this.entryMode = entryMode;
    }

    public String getPatternImageUrl() {
        return patternImageUrl;
    }

    public void setPatternImageUrl(String patternImageUrl) {
        this.patternImageUrl = patternImageUrl;
    }

    public String getPatternImageSourceType() {
        return patternImageSourceType;
    }

    public void setPatternImageSourceType(String patternImageSourceType) {
        this.patternImageSourceType = patternImageSourceType;
    }

    public Long getSourcePatternId() {
        return sourcePatternId;
    }

    public void setSourcePatternId(Long sourcePatternId) {
        this.sourcePatternId = sourcePatternId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStoryText() {
        return storyText;
    }

    public void setStoryText(String storyText) {
        this.storyText = storyText;
    }

    public String getStoryFileUrl() {
        return storyFileUrl;
    }

    public void setStoryFileUrl(String storyFileUrl) {
        this.storyFileUrl = storyFileUrl;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getIsVisible() {
        return isVisible;
    }

    public Boolean getVisible() {
        return isVisible;
    }

    public void setIsVisible(Boolean isVisible) {
        this.isVisible = isVisible;
    }
}
