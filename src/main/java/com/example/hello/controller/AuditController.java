package com.example.hello.controller;

import java.util.List;

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

import com.example.hello.dto.AuditRequest;
import com.example.hello.dto.BatchAuditRequest;
import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.PatternPending;
import com.example.hello.enums.AuditStatus;
import com.example.hello.service.AuditService;
import com.example.hello.util.JwtUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService auditService;
    private final JwtUtil jwtUtil;

    public AuditController(AuditService auditService, JwtUtil jwtUtil) {
        this.auditService = auditService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 提交纹样待审核
     */
    @PostMapping("/submit")
    public ResponseEntity<PatternPending> submit(
            @Valid @RequestBody PatternRequest request,
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(auditService.submit(request, userId));
    }

    /**
     * 审核纹样
     */
    @PostMapping("/{id}/review")
    public ResponseEntity<?> audit(
            @PathVariable Long id,
            @Valid @RequestBody AuditRequest request,
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(auditService.audit(id, request, userId));
    }

    /**
     * 批量审核纹样
     */
    @PostMapping("/batch-review")
    public ResponseEntity<?> batchAudit(
            @Valid @RequestBody BatchAuditRequest request,
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        auditService.batchAudit(request, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 重新提交被拒绝的纹样
     */
    @PutMapping("/{id}/resubmit")
    public ResponseEntity<PatternPending> resubmit(
            @PathVariable Long id,
            @Valid @RequestBody PatternRequest request,
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(auditService.resubmit(id, request, userId));
    }

    /**
     * 获取待审核列表
     */
    @GetMapping("/pending")
    public ResponseEntity<List<PatternPending>> findPending() {
        return ResponseEntity.ok(auditService.findPending());
    }

    /**
     * 获取所有审核记录
     */
    @GetMapping
    public ResponseEntity<List<PatternPending>> findAll() {
        return ResponseEntity.ok(auditService.findAll());
    }

    /**
     * 按状态查询
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<PatternPending>> findByStatus(@PathVariable String status) {
        AuditStatus auditStatus = AuditStatus.valueOf(status.toUpperCase());
        return ResponseEntity.ok(auditService.findByStatus(auditStatus));
    }

    /**
     * 查询我的提交记录
     */
    @GetMapping("/my")
    public ResponseEntity<List<PatternPending>> findMySubmissions(
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(auditService.findBySubmitter(userId));
    }

    /**
     * 查询我最近录入的记录（最多100条，按时间倒序）
     */
    @GetMapping("/my/recent")
    public ResponseEntity<List<PatternPending>> findMyRecentSubmissions(
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(auditService.findRecentBySubmitter(userId));
    }

    /**
     * 根据ID查询
     */
    @GetMapping("/{id}")
    public ResponseEntity<PatternPending> findById(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.findById(id));
    }

    /**
     * 删除待审核记录
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        auditService.delete(id, userId);
        return ResponseEntity.ok().build();
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }
}
