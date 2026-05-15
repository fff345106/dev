package com.example.hello.dto;

import jakarta.validation.constraints.Size;

public class ArticleUpdateRequest {
    @Size(max = 200, message = "文章标题不能超过200个字符")
    private String title;

    private String content;

    @Size(max = 500, message = "文章摘要不能超过500个字符")
    private String summary;

    @Size(max = 500, message = "封面图片URL不能超过500个字符")
    private String coverUrl;

    private String status;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
