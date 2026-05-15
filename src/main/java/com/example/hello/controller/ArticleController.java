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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.ArticleCreateRequest;
import com.example.hello.dto.ArticleResponse;
import com.example.hello.dto.ArticleUpdateRequest;
import com.example.hello.enums.ArticleStatus;
import com.example.hello.service.ArticleService;
import com.example.hello.util.JwtUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {
    private final ArticleService articleService;
    private final JwtUtil jwtUtil;

    public ArticleController(ArticleService articleService, JwtUtil jwtUtil) {
        this.articleService = articleService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<Page<ArticleResponse>> list(
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) ArticleStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(articleService.list(authorId, status, pageable));
    }

    @PostMapping
    public ResponseEntity<ArticleResponse> create(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody ArticleCreateRequest request) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(articleService.create(request, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArticleResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(articleService.getDetail(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ArticleResponse> update(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody ArticleUpdateRequest request) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(articleService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        articleService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }
}
