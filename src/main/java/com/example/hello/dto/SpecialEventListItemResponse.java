package com.example.hello.dto;

import com.example.hello.entity.SpecialEvent;

public class SpecialEventListItemResponse {

    private Long id;
    private String title;
    private String desc;
    private String image;
    private String url;

    public static SpecialEventListItemResponse fromEntity(SpecialEvent event) {
        SpecialEventListItemResponse response = new SpecialEventListItemResponse();
        response.setId(event.getId());
        response.setTitle(defaultText(event.getTitle(), "未命名活动"));
        response.setDesc(defaultText(event.getDescription(), "暂无活动简介。"));
        response.setImage(trimToNull(event.getImageUrl()));
        response.setUrl(trimToNull(event.getUrl()));
        return response;
    }

    private static String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

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

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
