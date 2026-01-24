package com.example.hello.controller;

import java.util.List;
import java.util.Map;

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

import com.example.hello.dto.DraftRequest;
import com.example.hello.entity.PatternDraft;
import com.example.hello.entity.PatternPending;
import com.example.hello.service.DraftService;
import com.example.hello.util.JwtUtil;

@RestController
@RequestMapping("/api/drafts")
public class DraftController {
    private final DraftService draftService;
    private final JwtUtil jwtUtil;

    public DraftController(DraftService draftService, JwtUtil jwtUtil) {
        this.draftService = draftService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 保存草稿
     */
    @PostMapping
    public ResponseEntity<?> save(
            @RequestBody DraftRequest request,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = getUserIdFromToken(token);
            return ResponseEntity.ok(draftService.save(request, userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 更新草稿
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody DraftRequest request,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = getUserIdFromToken(token);
            return ResponseEntity.ok(draftService.update(id, request, userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 获取我的草稿列表
     */
    @GetMapping
    public ResponseEntity<List<PatternDraft>> findMyDrafts(
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(draftService.findByUser(userId));
    }

    /**
     * 获取单个草稿
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> findById(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = getUserIdFromToken(token);
            return ResponseEntity.ok(draftService.findById(id, userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 删除草稿
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = getUserIdFromToken(token);
            draftService.delete(id, userId);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 提交草稿到审核
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submitToAudit(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = getUserIdFromToken(token);
            PatternPending pending = draftService.submitToAudit(id, userId);
            return ResponseEntity.ok(pending);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }
}
