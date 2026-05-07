package com.example.hello.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.EnterpriseAuthRequest;
import com.example.hello.dto.MasterAuthRequest;
import com.example.hello.dto.RealNameAuthRequest;
import com.example.hello.entity.User;
import com.example.hello.entity.UserCertification;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserRepository;
import com.example.hello.service.CertificationService;
import com.example.hello.util.JwtUtil;

@RestController
@RequestMapping("/api/certifications")
public class CertificationController {

    private final CertificationService certificationService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public CertificationController(CertificationService certificationService,
                                   JwtUtil jwtUtil,
                                   UserRepository userRepository) {
        this.certificationService = certificationService;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    /**
     * 提交实名认证（任意已登录用户）
     */
    @PostMapping("/real-name")
    public ResponseEntity<?> submitRealNameAuth(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody RealNameAuthRequest request) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long userId = getUserIdFromToken(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            UserCertification cert = certificationService.submitRealNameAuth(user, request);
            return ResponseEntity.ok(toResponse(cert));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 提交企业认证（仅企商用户）
     */
    @PostMapping("/enterprise")
    public ResponseEntity<?> submitEnterpriseAuth(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody EnterpriseAuthRequest request) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long userId = getUserIdFromToken(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            if (user.getRole() != UserRole.ENTERPRISE_USER) {
                return ResponseEntity.status(403).body(Map.of("message", "仅企商用户可提交企业认证"));
            }
            UserCertification cert = certificationService.submitEnterpriseAuth(user, request);
            return ResponseEntity.ok(toResponse(cert));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 提交技艺认证（仅技艺大师）
     */
    @PostMapping("/master")
    public ResponseEntity<?> submitMasterAuth(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody MasterAuthRequest request) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long userId = getUserIdFromToken(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            if (user.getRole() != UserRole.MASTER_ARTISAN) {
                return ResponseEntity.status(403).body(Map.of("message", "仅技艺大师可提交技艺认证"));
            }
            UserCertification cert = certificationService.submitMasterAuth(user, request);
            return ResponseEntity.ok(toResponse(cert));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 获取我的认证记录列表
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyCertifications(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long userId = getUserIdFromToken(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            List<UserCertification> certs = certificationService.getMyCertifications(user);
            return ResponseEntity.ok(certs.stream().map(this::toResponse).toList());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 获取待审核认证列表（仅管理员/超级管理员）
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingCertifications(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long userId = getUserIdFromToken(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            if (user.getRole() != UserRole.SUPER_ADMIN && user.getRole() != UserRole.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("message", "权限不足"));
            }
            List<UserCertification> certs = certificationService.getPendingCertifications();
            return ResponseEntity.ok(certs.stream().map(this::toResponse).toList());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 审核通过认证（仅管理员/超级管理员）
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveCertification(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long userId = getUserIdFromToken(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            if (user.getRole() != UserRole.SUPER_ADMIN && user.getRole() != UserRole.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("message", "权限不足"));
            }
            UserCertification cert = certificationService.approveCertification(id, userId);
            Map<String, Object> response = toResponse(cert);
            response.put("message", "认证审核通过");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 审核拒绝认证（仅管理员/超级管理员）
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectCertification(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long userId = getUserIdFromToken(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            if (user.getRole() != UserRole.SUPER_ADMIN && user.getRole() != UserRole.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("message", "权限不足"));
            }
            String rejectReason = request.get("rejectReason");
            UserCertification cert = certificationService.rejectCertification(id, userId, rejectReason);
            Map<String, Object> response = toResponse(cert);
            response.put("message", "认证已拒绝");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }

    private Map<String, Object> toResponse(UserCertification cert) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", cert.getId());
        map.put("certificationType", cert.getCertificationType());
        map.put("status", cert.getStatus());
        map.put("realNameVerified", cert.getRealNameVerified());
        map.put("rejectReason", cert.getRejectReason());
        map.put("createdAt", cert.getCreatedAt());
        map.put("updatedAt", cert.getUpdatedAt());
        return map;
    }
}
