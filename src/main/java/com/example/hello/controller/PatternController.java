package com.example.hello.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.Pattern;
import com.example.hello.enums.UserRole;
import com.example.hello.service.PatternService;
import com.example.hello.util.JwtUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/patterns")
public class PatternController {
    private final PatternService patternService;
    private final JwtUtil jwtUtil;

    public PatternController(PatternService patternService, JwtUtil jwtUtil) {
        this.patternService = patternService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<Pattern> create(@Valid @RequestBody PatternRequest request) {
        return ResponseEntity.ok(patternService.create(request));
    }

    @GetMapping
    public ResponseEntity<Page<Pattern>> findAll(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(patternService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pattern> findById(@PathVariable Long id) {
        return ResponseEntity.ok(patternService.findById(id));
    }

    @GetMapping(value = "/{id}/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCode(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(patternService.generatePatternQrCode(id));
    }

    @GetMapping(value = "/code/{code}/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCodeByCode(@PathVariable String code) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(patternService.generatePatternQrCodeByCode(code));
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
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        UserRole role = checkPermission(token);
        patternService.delete(id, role);
        return ResponseEntity.ok().build();
    }

    /**
     * 批量删除纹样
     */
    @PostMapping("/batch-delete")
    public ResponseEntity<Void> batchDelete(
            @RequestBody java.util.List<Long> ids,
            @RequestHeader(value = "Authorization", required = false) String token) {
        UserRole role = checkPermission(token);
        patternService.batchDelete(ids, role);
        return ResponseEntity.ok().build();
    }

    private UserRole checkPermission(String token) {
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("未提供认证令牌");
        }
        try {
            String jwt = token.replace("Bearer ", "");
            String roleStr = jwtUtil.extractRole(jwt);
            if (roleStr == null) {
                throw new RuntimeException("无效的角色信息");
            }
            UserRole role = UserRole.valueOf(roleStr);
            if (role == UserRole.GUEST) {
                throw new RuntimeException("游客无权执行此操作");
            }
            return role;
        } catch (Exception e) {
            throw new RuntimeException("权限验证失败: " + e.getMessage());
        }
    }

    @GetMapping("/category/{mainCategory}")
    public ResponseEntity<Page<Pattern>> findByMainCategory(@PathVariable String mainCategory, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(patternService.findByMainCategory(mainCategory, pageable));
    }

    @GetMapping("/style/{style}")
    public ResponseEntity<Page<Pattern>> findByStyle(@PathVariable String style, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(patternService.findByStyle(style, pageable));
    }

    @GetMapping("/region/{region}")
    public ResponseEntity<Page<Pattern>> findByRegion(@PathVariable String region, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(patternService.findByRegion(region, pageable));
    }

    @GetMapping("/period/{period}")
    public ResponseEntity<Page<Pattern>> findByPeriod(@PathVariable String period, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(patternService.findByPeriod(period, pageable));
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
