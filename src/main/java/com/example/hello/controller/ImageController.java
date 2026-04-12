package com.example.hello.controller;

import com.example.hello.service.ImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    /**
     * 上传图片到对象存储
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        try {
            String url = imageService.upload(file);
            return ResponseEntity.ok(Map.of("url", url, "message", "上传成功"));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "上传失败: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 上传故事文件（图片/PDF）到对象存储
     */
    @PostMapping("/upload-story-file")
    public ResponseEntity<Map<String, String>> uploadStoryFile(@RequestParam("file") MultipartFile file) {
        try {
            String url = imageService.uploadStoryFile(file);
            return ResponseEntity.ok(Map.of("url", url, "message", "上传成功"));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "上传失败: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 删除图片
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> delete(@RequestParam("url") String url) {
        try {
            imageService.delete(url);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "删除失败: " + e.getMessage()));
        }
    }
}
