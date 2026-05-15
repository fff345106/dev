package com.example.hello.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.CollaborationCreateRequest;
import com.example.hello.dto.CollaborationResponse;
import com.example.hello.dto.CollaborationUpdateRequest;
import com.example.hello.enums.CollaborationStatus;
import com.example.hello.service.CollaborationService;
import com.example.hello.util.JwtUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/collaborations")
public class CollaborationController {

    private final CollaborationService collaborationService;
    private final JwtUtil jwtUtil;

    public CollaborationController(CollaborationService collaborationService, JwtUtil jwtUtil) {
        this.collaborationService = collaborationService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 查询合作列表（支持分页，可按状态过滤）
     */
    @GetMapping
    public ResponseEntity<Page<CollaborationResponse>> list(
            @RequestParam(required = false) CollaborationStatus status,
            @NonNull @RequestHeader("Authorization") String token,
            @NonNull @PageableDefault(size = 20) Pageable pageable) {
        Long userId = getUserIdFromToken(token);
        String role = getRoleFromToken(token);
        return ResponseEntity.ok(collaborationService.list(status, userId, role, pageable));
    }

    /**
     * 创建合作申请（仅企商用户）
     */
    @PostMapping
    public ResponseEntity<CollaborationResponse> create(
            @NonNull @Valid @RequestBody CollaborationCreateRequest request,
            @NonNull @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(collaborationService.create(request, userId));
    }

    /**
     * 更新合作状态（状态机控制）
     */
    @PutMapping("/{id}")
    public ResponseEntity<CollaborationResponse> updateStatus(
            @NonNull @PathVariable Long id,
            @NonNull @Valid @RequestBody CollaborationUpdateRequest request,
            @NonNull @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(collaborationService.updateStatus(id, request, userId));
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }

    private String getRoleFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractRole(jwt);
    }
}
