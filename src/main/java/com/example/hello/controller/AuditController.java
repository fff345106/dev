package com.example.hello.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.AiBatchConfirmRequest;
import com.example.hello.dto.AiBatchPreviewRequest;
import com.example.hello.dto.AiBatchPreviewResponse;
import com.example.hello.dto.AiBatchSubmitRequest;
import com.example.hello.dto.AiBatchSubmitResponse;
import com.example.hello.dto.AiBatchTaskProgressResponse;
import com.example.hello.dto.AiBatchTaskStartResponse;
import com.example.hello.dto.AuditRequest;
import com.example.hello.dto.BatchAuditRequest;
import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.PatternPending;
import com.example.hello.enums.AuditStatus;
import com.example.hello.service.AiBatchEntryService;
import com.example.hello.service.AuditService;
import com.example.hello.util.JwtUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService auditService;
    private final AiBatchEntryService aiBatchEntryService;
    private final JwtUtil jwtUtil;

    public AuditController(AuditService auditService, AiBatchEntryService aiBatchEntryService, JwtUtil jwtUtil) {
        this.auditService = auditService;
        this.aiBatchEntryService = aiBatchEntryService;
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
     * AI 批量识别预览
     */
    @PostMapping("/ai-batch-preview")
    public ResponseEntity<AiBatchPreviewResponse> aiBatchPreview(
            @Valid @RequestBody AiBatchPreviewRequest request) {
        return ResponseEntity.ok(aiBatchEntryService.preview(request));
    }

    /**
     * AI 批量确认提交纹样待审核
     */
    @PostMapping("/ai-batch-confirm")
    public ResponseEntity<AiBatchSubmitResponse> aiBatchConfirm(
            @Valid @RequestBody AiBatchConfirmRequest request,
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(aiBatchEntryService.confirm(request, userId));
    }

    /**
     * AI 批量提交纹样待审核（兼容旧入口）
     */
    @PostMapping("/ai-batch-submit")
    public ResponseEntity<AiBatchSubmitResponse> aiBatchSubmit(
            @Valid @RequestBody AiBatchSubmitRequest request,
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(aiBatchEntryService.submit(request, userId));
    }

    /**
     * AI 批量提交异步任务启动（用于前端进度展示）
     */
    @PostMapping("/ai-batch-submit/start")
    public ResponseEntity<AiBatchTaskStartResponse> aiBatchSubmitStart(
            @Valid @RequestBody AiBatchSubmitRequest request,
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(aiBatchEntryService.startSubmitTask(request, userId));
    }

    /**
     * AI 批量提交异步任务进度查询
     */
    @GetMapping("/ai-batch-submit/progress/{taskId}")
    public ResponseEntity<AiBatchTaskProgressResponse> aiBatchSubmitProgress(
            @PathVariable String taskId,
            @RequestHeader("Authorization") String token) {
        getUserIdFromToken(token);
        return ResponseEntity.ok(aiBatchEntryService.getSubmitTaskProgress(taskId));
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
        return ResponseEntity.ok(java.util.Map.of("message", "批量审核成功"));
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
    public ResponseEntity<Page<PatternPending>> findPending(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(auditService.findPending(pageable));
    }

    /**
     * 获取所有审核记录
     */
    @GetMapping
    public ResponseEntity<Page<PatternPending>> findAll(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(auditService.findAll(pageable));
    }

    /**
     * 按状态查询
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<PatternPending>> findByStatus(@PathVariable String status, @PageableDefault(size = 20) Pageable pageable) {
        AuditStatus auditStatus = AuditStatus.valueOf(status.toUpperCase());
        return ResponseEntity.ok(auditService.findByStatus(auditStatus, pageable));
    }

    /**
     * 查询我的提交记录
     */
    @GetMapping("/my")
    public ResponseEntity<Page<PatternPending>> findMySubmissions(
            @RequestHeader("Authorization") String token, @PageableDefault(size = 20) Pageable pageable) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(auditService.findBySubmitter(userId, pageable));
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

    /**
     * 批量删除待审核记录
     */
    @PostMapping("/batch-delete")
    public ResponseEntity<Void> batchDelete(
            @RequestBody java.util.List<Long> ids,
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        auditService.batchDelete(ids, userId);
        return ResponseEntity.ok().build();
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }
}
