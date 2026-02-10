package com.example.hello.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.Pattern;
import com.example.hello.service.PatternService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/patterns")
public class PatternController {
    private final PatternService patternService;

    public PatternController(PatternService patternService) {
        this.patternService = patternService;
    }

    @PostMapping
    public ResponseEntity<Pattern> create(@Valid @RequestBody PatternRequest request) {
        return ResponseEntity.ok(patternService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<Pattern>> findAll() {
        return ResponseEntity.ok(patternService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pattern> findById(@PathVariable Long id) {
        return ResponseEntity.ok(patternService.findById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<Pattern> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(patternService.findByCode(code));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Pattern> update(@PathVariable Long id, @Valid @RequestBody PatternRequest request) {
        return ResponseEntity.ok(patternService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patternService.delete(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/category/{mainCategory}")
    public ResponseEntity<List<Pattern>> findByMainCategory(@PathVariable String mainCategory) {
        return ResponseEntity.ok(patternService.findByMainCategory(mainCategory));
    }

    @GetMapping("/style/{style}")
    public ResponseEntity<List<Pattern>> findByStyle(@PathVariable String style) {
        return ResponseEntity.ok(patternService.findByStyle(style));
    }

    @GetMapping("/region/{region}")
    public ResponseEntity<List<Pattern>> findByRegion(@PathVariable String region) {
        return ResponseEntity.ok(patternService.findByRegion(region));
    }

    @GetMapping("/period/{period}")
    public ResponseEntity<List<Pattern>> findByPeriod(@PathVariable String period) {
        return ResponseEntity.ok(patternService.findByPeriod(period));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> download(@PathVariable Long id) {
        try {
            java.util.Map<String, Object> result = patternService.download(id);
            java.io.InputStream inputStream = (java.io.InputStream) result.get("stream");
            String filename = (String) result.get("filename");
            String contentType = (String) result.get("contentType");

            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .body(new org.springframework.core.io.InputStreamResource(inputStream));
        } catch (java.io.IOException e) {
            throw new RuntimeException("下载失败: " + e.getMessage());
        }
    }

    /**
     * 批量下载纹样图片
     */
    @PostMapping("/batch-download")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> batchDownload(
            @Valid @RequestBody com.example.hello.dto.BatchDownloadRequest request) {
        
        org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody stream = outputStream -> {
            patternService.batchDownload(request.getIds(), outputStream);
        };

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"patterns.zip\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
                .body(stream);
    }
}
