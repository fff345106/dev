package com.example.hello.dto;

public class DraftRequest {
    private String description;
    private String mainCategory;
    private String subCategory;
    private String style;
    private String region;
    private String period;
    private String imageUrl;
    private String imageSourceType; // 图片来源: TEMP_UPLOAD/EXTERNAL/LIBRARY
    private String storyText; // 藏品故事-文字描述
    private String storyImageUrl; // 藏品故事-图片 (支持PDF)

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
}
