package com.example.hello.controller;

import java.util.Map;

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

import com.example.hello.enums.UserRole;
import com.example.hello.service.InvitationCodeService;
import com.example.hello.service.UserService;
import com.example.hello.util.JwtUtil;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final InvitationCodeService invitationCodeService;

    public UserController(UserService userService, JwtUtil jwtUtil, InvitationCodeService invitationCodeService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.invitationCodeService = invitationCodeService;
    }

    /**
     * 删除用户账号
     * 超级管理员可以删除其他用户（除了超级管理员）
     * 用户可以删除自己的账号
     */
    @DeleteMapping("/{userId:\\d+}")
    public ResponseEntity<?> deleteUser(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long operatorUserId = getUserIdFromToken(token);
            userService.deleteUser(userId, operatorUserId);
            return ResponseEntity.ok(Map.of("message", "账号删除成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/{userId:\\d+}")
    public ResponseEntity<?> getUserInfo(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(userService.getUserById(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 获取所有用户列表（仅超级管理员可操作）
     */
    @GetMapping
    public ResponseEntity<?> getAllUsers(
            @RequestHeader(value = "Authorization", required = false) String token, 
            @PageableDefault(size = 20) Pageable pageable) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long operatorUserId = getUserIdFromToken(token);
            return ResponseEntity.ok(userService.getAllUsers(operatorUserId, pageable));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 设置用户角色（仅超级管理员可操作）
     */
    @PutMapping("/{userId:\\d+}/role")
    public ResponseEntity<?> setUserRole(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long operatorUserId = getUserIdFromToken(token);
            String roleStr = request.get("role");
            if (roleStr == null || roleStr.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "角色不能为空"));
            }
            UserRole role = UserRole.valueOf(roleStr.toUpperCase());
            return ResponseEntity.ok(userService.setUserRole(userId, role, operatorUserId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "无效的角色: " + request.get("role")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 生成邀请码（仅超级管理员可操作）
     */
    @PostMapping("/invite-codes")
    public ResponseEntity<?> generateInvitationCode(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long operatorUserId = getUserIdFromToken(token);
            String code = invitationCodeService.generateCode(operatorUserId);
            return ResponseEntity.ok(Map.of("message", "邀请码生成成功", "code", code));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }
}
