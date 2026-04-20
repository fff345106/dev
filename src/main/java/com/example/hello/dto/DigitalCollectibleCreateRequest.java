package com.example.hello.dto;

import com.example.hello.enums.CollectibleEntryMode;

import jakarta.validation.constraints.NotBlank;

public class DigitalCollectibleCreateRequest {

    private CollectibleEntryMode entryMode;

    @NotBlank(message = "纹样图片不能为空")
    private String patternImageUrl;

    private String patternImageSourceType;

    private Long sourcePatternId;

    private String description;

    private String storyText;

    private String storyFileUrl;

    public CollectibleEntryMode getEntryMode() {
        return entryMode;
    }

    public void setEntryMode(CollectibleEntryMode entryMode) {
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
}
