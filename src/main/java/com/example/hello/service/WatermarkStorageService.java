package com.example.hello.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.springframework.stereotype.Service;

import com.example.hello.dto.WatermarkResult;

@Service
public class WatermarkStorageService {

    private final ImageService imageService;
    private final DwtSvdWatermarkService dwtSvdWatermarkService;

    public WatermarkStorageService(ImageService imageService, DwtSvdWatermarkService dwtSvdWatermarkService) {
        this.imageService = imageService;
        this.dwtSvdWatermarkService = dwtSvdWatermarkService;
    }

    /**
     * 从 S3 下载原图 -> 嵌入隐形水印 -> 上传水印版本到 S3 -> 返回双 URL
     *
     * @param originalUrl 原图的 S3 URL
     * @param patternCode 纹样编码
     * @param uploaderId  上传者用户 ID
     * @return WatermarkResult 包含原图 URL 和水印图 URL
     * @throws IOException 如果原图下载失败（阻止入库）
     */
    public WatermarkResult embedAndStore(String originalUrl, String patternCode, Long uploaderId) throws IOException {
        // 1. 提取原图 key
        String originalKey = imageService.extractKeyFromUrl(originalUrl);

        // 2. 下载原图字节
        byte[] originalBytes;
        try (var inputStream = imageService.download(originalUrl)) {
            originalBytes = inputStream.readAllBytes();
        }

        // 3. 构建水印文本
        String watermarkText = buildWatermarkText(patternCode, uploaderId);

        // 4. 嵌入水印
        byte[] watermarkedBytes;
        try {
            watermarkedBytes = dwtSvdWatermarkService.embed(
                    new ByteArrayInputStream(originalBytes), watermarkText, ".png");
        } catch (Exception e) {
            // 水印嵌入失败，降级处理：记录日志，返回 null 水印 URL
            System.err.println("水印嵌入失败，降级使用原图: " + e.getMessage());
            return new WatermarkResult(originalUrl, null);
        }

        // 5. 上传水印版本到 S3
        String watermarkedKey = imageService.toWatermarkedKey(originalKey);
        String watermarkedUrl;
        try {
            watermarkedUrl = imageService.uploadBytes(watermarkedKey, watermarkedBytes, "image/png");
        } catch (IOException e) {
            // 水印图上传失败，降级处理
            System.err.println("水印图上传 S3 失败，降级使用原图: " + e.getMessage());
            return new WatermarkResult(originalUrl, null);
        }

        return new WatermarkResult(originalUrl, watermarkedUrl);
    }

    /**
     * 构建水印文本：WM:<patternCode>:<uploaderId>
     */
    String buildWatermarkText(String patternCode, Long uploaderId) {
        String code = (patternCode == null) ? "" : patternCode.trim();
        String userId = (uploaderId == null) ? "0" : uploaderId.toString();
        if (code.isEmpty()) {
            return "WM::" + userId;
        }
        return "WM:" + code + ":" + userId;
    }
}
