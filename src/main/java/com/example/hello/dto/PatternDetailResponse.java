package com.example.hello.dto;

import java.util.List;

public class PatternDetailResponse {
    private Long id;
    private String title;
    private String patternCode;
    private String image;
    private String watermarkedUrl;
    private String desc;
    private List<String> story;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPatternCode() {
        return patternCode;
    }

    public void setPatternCode(String patternCode) {
        this.patternCode = patternCode;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getWatermarkedUrl() { return watermarkedUrl; }
    public void setWatermarkedUrl(String watermarkedUrl) { this.watermarkedUrl = watermarkedUrl; }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public List<String> getStory() {
        return story;
    }

    public void setStory(List<String> story) {
        this.story = story;
    }
}
