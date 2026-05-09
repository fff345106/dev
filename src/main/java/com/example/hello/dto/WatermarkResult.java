package com.example.hello.dto;

public class WatermarkResult {
    private final String originalUrl;
    private final String watermarkedUrl;

    public WatermarkResult(String originalUrl, String watermarkedUrl) {
        this.originalUrl = originalUrl;
        this.watermarkedUrl = watermarkedUrl;
    }

    public String getOriginalUrl() { return originalUrl; }
    public String getWatermarkedUrl() { return watermarkedUrl; }
}
