package com.example.hello.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AiBatchSubmitRequest {
    @NotEmpty(message = "图片URL列表不能为空")
    private List<@NotBlank(message = "图片URL不能为空") String> imageUrls;

    @Size(min = 2, max = 2, message = "风格代码必须为2位")
    private String style;

    @Size(min = 2, max = 2, message = "地区代码必须为2位")
    private String region;

    @Size(min = 2, max = 2, message = "时期代码必须为2位")
    private String period;

    private String descriptionPrefix;

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getDescriptionPrefix() {
        return descriptionPrefix;
    }

    public void setDescriptionPrefix(String descriptionPrefix) {
        this.descriptionPrefix = descriptionPrefix;
    }
}
