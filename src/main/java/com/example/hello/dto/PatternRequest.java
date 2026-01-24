package com.example.hello.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PatternRequest {
    private String description;

    @NotBlank(message = "主类别代码不能为空")
    @Size(min = 2, max = 2, message = "主类别代码必须为2位")
    private String mainCategory;

    @NotBlank(message = "子类别不能为空")
    @Size(min = 2, max = 2, message = "子类别代码必须为2位")
    private String subCategory;

    @NotBlank(message = "风格不能为空")
    @Size(min = 2, max = 2, message = "风格代码必须为2位")
    private String style;

    @NotBlank(message = "地区不能为空")
    @Size(min = 2, max = 2, message = "地区代码必须为2位")
    private String region;

    @NotBlank(message = "时期不能为空")
    @Size(min = 2, max = 2, message = "时期代码必须为2位")
    private String period;

    private String imageUrl;

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
}
