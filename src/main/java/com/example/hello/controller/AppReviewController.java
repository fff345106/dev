package com.example.hello.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.AppReviewRequest;
import com.example.hello.dto.AppReviewStatsResponse;
import com.example.hello.entity.AppReview;
import com.example.hello.enums.UserRole;
import com.example.hello.service.AppReviewService;
import com.example.hello.util.JwtUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reviews")
public class AppReviewController {

    private final AppReviewService appReviewService;
    private final JwtUtil jwtUtil;

    public AppReviewController(AppReviewService appReviewService, JwtUtil jwtUtil) {
        this.appReviewService = appReviewService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 提交评价
     */
    @PostMapping
    public ResponseEntity<?> submitReview(
            @NonNull @Valid @RequestBody AppReviewRequest request,
            @NonNull @RequestHeader("Authorization") String token) {
        try {
            Long userId = getUserIdFromToken(token);
            AppReview review = appReviewService.submitReview(userId, request.getRating(), request.getComment());
            return ResponseEntity.status(HttpStatus.CREATED).body(review);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 评价列表（分页，按时间倒序）
     */
    @GetMapping
    public ResponseEntity<Page<AppReview>> getReviews(
            @NonNull @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(appReviewService.getReviews(pageable));
    }

    /**
     * 平均评分统计
     */
    @GetMapping("/stats")
    public ResponseEntity<AppReviewStatsResponse> getStats() {
        return ResponseEntity.ok(appReviewService.getStats());
    }

    /**
     * 我的评价列表
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyReviews(
            @NonNull @RequestHeader("Authorization") String token,
            @NonNull @PageableDefault(size = 20) Pageable pageable) {
        try {
            Long userId = getUserIdFromToken(token);
            return ResponseEntity.ok(appReviewService.getMyReviews(userId, pageable));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 删除评价（仅管理员）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(
            @NonNull @PathVariable Long id,
            @NonNull @RequestHeader("Authorization") String token) {
        try {
            String jwt = token.replace("Bearer ", "");
            UserRole role = UserRole.valueOf(jwtUtil.extractRole(jwt));
            appReviewService.deleteReview(id, role);
            return ResponseEntity.ok(Map.of("message", "评价删除成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }
}
