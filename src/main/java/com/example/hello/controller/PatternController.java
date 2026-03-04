package com.example.hello.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
        checkPermission(token);
        patternService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 批量删除纹样
     */
    @PostMapping("/batch-delete")
    public ResponseEntity<Void> batchDelete(
            @RequestBody java.util.List<Long> ids,
            @RequestHeader(value = "Authorization", required = false) String token) {
        checkPermission(token);
        patternService.batchDelete(ids);
        return ResponseEntity.ok().build();
    }

    private void checkPermission(String token) {
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("未提供认证令牌");
        }
        try {
            String jwt = token.replace("Bearer ", "");
            // 简单的角色验证，更严谨的应该在 SecurityConfig 中配置或使用 @PreAuthorize
            // 这里为了快速实现且不改动太多配置，手动解析
            // 注意：JwtUtil 需要能处理解析异常
            // 这里我们通过反射获取 JwtUtil 的 extractRole 方法，或者直接调用
            // 假设 JwtUtil 已经注入并可用
            
            // 下面这行代码依赖于 JwtUtil 的实现细节，如果 extractRole 抛出异常会被捕获
            // 为了获取 role，我们需要调用 jwtUtil.extractRole(jwt)
            // 但 extractRole 返回 String，我们需要转成 UserRole
            
            // 重新解析 Token 以获取 Claims (如果 JwtUtil 没有公开 extractClaims)
            // 其实可以直接调用 jwtUtil.extractRole(jwt)
            String roleStr = jwtUtil.extractRole(jwt);
            if (roleStr == null) {
                throw new RuntimeException("无效的角色信息");
            }
            UserRole role = UserRole.valueOf(roleStr);
            if (role == UserRole.GUEST) {
                throw new RuntimeException("游客无权执行此操作");
            }
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
