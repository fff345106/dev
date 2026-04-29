package com.example.hello.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class SpecialEventCreateRequest {

    @JsonAlias({"name"})
    private String title;

    @JsonAlias({"description", "summary"})
    private String desc;

    @JsonAlias({"cover", "coverUrl", "imageUrl"})
    private String image;

    @JsonAlias({"url", "link"})
    private String url;

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
