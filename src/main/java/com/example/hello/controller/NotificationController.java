package com.example.hello.controller;

import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.NotificationListResponse;
import com.example.hello.enums.NotificationType;
import com.example.hello.service.NotificationService;
import com.example.hello.util.JwtUtil;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    public NotificationController(NotificationService notificationService, JwtUtil jwtUtil) {
        this.notificationService = notificationService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 获取通知列表（支持按类型和已读状态筛选，分页）
     */
    @GetMapping
    public ResponseEntity<NotificationListResponse> list(
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) Boolean isRead,
            @PageableDefault(size = 20) Pageable pageable,
            @NonNull @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        NotificationListResponse response = notificationService.list(userId, type, isRead, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * 标记单条通知为已读
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(
            @NonNull @PathVariable Long id,
            @NonNull @RequestHeader("Authorization") String token) {
        try {
            Long userId = getUserIdFromToken(token);
            notificationService.markAsRead(id, userId);
            return ResponseEntity.ok(Map.of("message", "已标记为已读"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 标记所有通知为已读
     */
    @PutMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(
            @NonNull @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("message", "已全部标记为已读", "count", count));
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }
}
