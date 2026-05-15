package com.example.hello.dto;

public class PatternRankingResponse {
    private int rank;
    private Long id;
    private String patternCode;
    private String description;
    private String imageUrl;
    private String watermarkedUrl;
    private Long viewCount;
    private Long likeCount;
    private Long downloadCount;
    private double score;
    private String authorName;

    public PatternRankingResponse() {}

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPatternCode() { return patternCode; }
    public void setPatternCode(String patternCode) { this.patternCode = patternCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getWatermarkedUrl() { return watermarkedUrl; }
    public void setWatermarkedUrl(String watermarkedUrl) { this.watermarkedUrl = watermarkedUrl; }
    public Long getViewCount() { return viewCount; }
    public void setViewCount(Long viewCount) { this.viewCount = viewCount; }
    public Long getLikeCount() { return likeCount; }
    public void setLikeCount(Long likeCount) { this.likeCount = likeCount; }
    public Long getDownloadCount() { return downloadCount; }
    public void setDownloadCount(Long downloadCount) { this.downloadCount = downloadCount; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
}
