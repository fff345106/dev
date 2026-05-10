package com.example.hello.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.EnterpriseAuthRequest;
import com.example.hello.dto.MasterAuthRequest;
import com.example.hello.dto.RealNameAuthRequest;
import com.example.hello.entity.User;
import com.example.hello.entity.UserCertification;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserRepository;
import com.example.hello.service.CertificationService;
import com.example.hello.service.ImageService;
import com.example.hello.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/certifications")
public class CertificationController {

    private final CertificationService certificationService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final ObjectMapper objectMapper;

    public CertificationController(CertificationService certificationService,
                                   JwtUtil jwtUtil,
                                   UserRepository userRepository,
                                   ImageService imageService,
                                   ObjectMapper objectMapper) {
        this.certificationService = certificationService;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.imageService = imageService;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交实名认证（任意已登录用户）
     */
    @RequestMapping(value = "/real-name", method = {RequestMethod.POST, RequestMethod.PUT}, consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> submitRealNameAuth(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody String body) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            RealNameAuthRequest request = objectMapper.readValue(body, RealNameAuthRequest.class);
            Long userId = getUserIdFromToken(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));

            System.out.println("[认证] 收到实名认证请求 idCardFrontUrl=" + request.getIdCardFrontUrl()
                    + " idCardBackUrl=" + request.getIdCardBackUrl());

            // 将临时图片移到 Identity 目录；若移动失败则保留原 URL
            String idCardFrontUrl = moveIdentityImageSafe(request.getIdCardFrontUrl(), userId, "idCardFront");
            String idCardBackUrl = moveIdentityImageSafe(request.getIdCardBackUrl(), userId, "idCardBack");

            UserCertification cert = certificationService.submitRealNameAuth(
                    user, request.getRealName(), request.getIdCardNumber(),
                    idCardFrontUrl, idCardBackUrl);
            return ResponseEntity.ok(toResponse(cert));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 提交企业认证（仅企商用户）
     */
    @RequestMapping(value = "/enterprise", method = {RequestMethod.POST, RequestMethod.PUT}, consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> submitEnterpriseAuth(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody String body) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            EnterpriseAuthRequest request = objectMapper.readValue(body, EnterpriseAuthRequest.class);
            Long userId = getUserIdFromToken(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            if (user.getRole() != UserRole.ENTERPRISE_USER) {
                return ResponseEntity.status(403).body(Map.of("message", "仅企商用户可提交企业认证"));
            }

            System.out.println("[认证] 收到企业认证请求 businessLicenseUrl=" + request.getBusinessLicenseUrl()
                    + " authorizationLetterUrl=" + request.getAuthorizationLetterUrl());

            // 将临时图片移到 Identity 目录；若移动失败则保留原 URL
            String businessLicenseUrl = moveIdentityImageSafe(request.getBusinessLicenseUrl(), userId, "businessLicense");
            String authorizationLetterUrl = moveIdentityImageSafe(request.getAuthorizationLetterUrl(), userId, "authorizationLetter");

            UserCertification cert = certificationService.submitEnterpriseAuth(
                    user, businessLicenseUrl, authorizationLetterUrl,
                    request.getLegalRepresentativeName(), request.getIsLegalRepresentative());
            return ResponseEntity.ok(toResponse(cert));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 提交技艺认证（仅技艺大师）
     */
    @RequestMapping(value = "/master", method = {RequestMethod.POST, RequestMethod.PUT}, consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> submitMasterAuth(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody String body) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            MasterAuthRequest request = objectMapper.readValue(body, MasterAuthRequest.class);
            Long userId = getUserIdFromToken(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            if (user.getRole() != UserRole.MASTER_ARTISAN) {
                return ResponseEntity.status(403).body(Map.of("message", "仅技艺大师可提交技艺认证"));
            }

            System.out.println("[认证] 收到技艺认证请求 certificationUrl=" + request.getCertificationUrl()
                    + " representativeWorkUrl=" + request.getRepresentativeWorkUrl());

            // 将临时图片移到 Identity 目录；若移动失败则保留原 URL
            String certificationUrl = moveIdentityImageSafe(request.getCertificationUrl(), userId, "certification");
            String representativeWorkUrl = moveIdentityImageSafe(request.getRepresentativeWorkUrl(), userId, "representativeWork");

            System.out.println("[认证] 移动后 certificationUrl=" + certificationUrl
                    + " representativeWorkUrl=" + representativeWorkUrl);

            UserCertification cert = certificationService.submitMasterAuth(
                    user, certificationUrl, representativeWorkUrl);
            return ResponseEntity.ok(toResponse(cert));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 获取我的认证记录列表
     */
    @RequestMapping(value = "/my", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT})
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
            return ResponseEntity.ok(certs.stream().map(this::toAdminResponse).toList());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 获取全部认证记录（仅管理员/超级管理员）
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllCertifications(
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
            List<UserCertification> certs = certificationService.getAllCertifications();
            return ResponseEntity.ok(certs.stream().map(this::toAdminResponse).toList());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 获取认证详情（仅管理员/超级管理员）
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCertificationDetail(
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
            UserCertification cert = certificationService.getCertificationById(id);
            return ResponseEntity.ok(toAdminResponse(cert));
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

    /**
     * 将临时图片移动到 Identity 目录，移动失败时保留原 URL
     */
    private String moveIdentityImageSafe(String tempUrl, Long userId, String imageType) {
        if (tempUrl == null || tempUrl.isBlank()) {
            return null;
        }
        try {
            String result = imageService.moveToIdentity(tempUrl, userId, imageType);
            System.out.println("[认证] 图片移动成功 " + imageType + ": " + tempUrl + " -> " + result);
            return result;
        } catch (Exception e) {
            // 移动失败时保留原始 URL，不影响认证提交
            System.err.println("[认证] 图片移动失败，保留原URL " + imageType + ": " + e.getMessage());
            return tempUrl;
        }
    }

    /**
     * 用户自己的认证详情（不含他人信息）
     */
    private Map<String, Object> toResponse(UserCertification cert) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", cert.getId());
        map.put("certificationType", cert.getCertificationType());
        map.put("status", cert.getStatus());
        map.put("rejectReason", cert.getRejectReason());
        map.put("createdAt", cert.getCreatedAt());
        map.put("updatedAt", cert.getUpdatedAt());
        // 实名认证
        map.put("realName", cert.getRealName());
        map.put("idCardNumber", cert.getIdCardNumber());
        map.put("idCardFrontUrl", cert.getIdCardFrontUrl());
        map.put("idCardBackUrl", cert.getIdCardBackUrl());
        map.put("realNameVerified", cert.getRealNameVerified());
        // 企业认证
        map.put("businessLicenseUrl", cert.getBusinessLicenseUrl());
        map.put("authorizationLetterUrl", cert.getAuthorizationLetterUrl());
        map.put("legalRepresentativeName", cert.getLegalRepresentativeName());
        map.put("isLegalRepresentative", cert.getIsLegalRepresentative());
        // 技艺认证
        map.put("certificationUrl", cert.getCertificationUrl());
        map.put("representativeWorkUrl", cert.getRepresentativeWorkUrl());
        return map;
    }

    /**
     * 管理员视角的认证详情（含提交人、审核人信息）
     */
    private Map<String, Object> toAdminResponse(UserCertification cert) {
        Map<String, Object> map = toResponse(cert);
        // 提交人信息
        if (cert.getUser() != null) {
            Map<String, Object> userMap = new java.util.LinkedHashMap<>();
            userMap.put("id", cert.getUser().getId());
            userMap.put("username", cert.getUser().getUsername());
            userMap.put("role", cert.getUser().getRole());
            map.put("user", userMap);
        }
        // 审核人信息
        map.put("auditorId", cert.getAuditorId());
        map.put("auditTime", cert.getAuditTime());
        return map;
    }
}
