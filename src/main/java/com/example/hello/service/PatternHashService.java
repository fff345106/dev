package com.example.hello.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Service;

@Service
public class PatternHashService {
    private static final String HASH_ALGORITHM = "SHA-256";

    private final ImageService imageService;

    public PatternHashService(ImageService imageService) {
        this.imageService = imageService;
    }

    public String hashAlgorithm() {
        return HASH_ALGORITHM;
    }

    public String computeSha256ByImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new IllegalArgumentException("图片URL不能为空，无法计算哈希");
        }

        try (InputStream inputStream = imageService.download(imageUrl)) {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (IOException e) {
            throw new RuntimeException("计算图片哈希失败: " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("不支持的哈希算法: " + HASH_ALGORITHM, e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
