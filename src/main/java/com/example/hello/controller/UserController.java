package com.example.hello.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
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
import org.springframework.web.multipart.MultipartFile;

import com.example.hello.dto.UpdateUsernameRequest;
import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.service.FeedService;
import com.example.hello.service.InvitationCodeService;
import com.example.hello.service.UserService;
import com.example.hello.util.JwtUtil;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final InvitationCodeService invitationCodeService;
    private final FeedService feedService;

    public UserController(UserService userService, JwtUtil jwtUtil, InvitationCodeService invitationCodeService, FeedService feedService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.invitationCodeService = invitationCodeService;
        this.feedService = feedService;
    }

    /**
     * 删除用户账号
     * 超级管理员可以删除其他用户（除了超级管理员）
     * 用户可以删除自己的账号
     */
    @DeleteMapping("/{userId:\\d+}")
    public ResponseEntity<?> deleteUser(
            @NonNull @PathVariable Long userId,
            @NonNull @RequestHeader(value = "Authorization", required = false) String token) {
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
    public ResponseEntity<?> getUserInfo(@NonNull @PathVariable Long userId) {
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
            @NonNull @RequestHeader(value = "Authorization", required = false) String token, 
            @NonNull @PageableDefault(size = 20) Pageable pageable) {
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
            @NonNull @PathVariable Long userId,
            @NonNull @RequestBody Map<String, String> request,
            @NonNull @RequestHeader(value = "Authorization", required = false) String token) {
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
            @NonNull @RequestHeader(value = "Authorization", required = false) String token) {
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

    /**
     * 修改用户名
     * 用户可以修改自己的用户名，超级管理员可以修改任意用户的用户名
     */
    @PutMapping("/{userId:\\d+}/username")
    public ResponseEntity<?> updateUsername(
            @NonNull @PathVariable Long userId,
            @NonNull @RequestBody @Valid UpdateUsernameRequest request,
            @NonNull @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long operatorUserId = getUserIdFromToken(token);
            User updatedUser = userService.updateUsername(userId, request.getUsername(), operatorUserId);
            return ResponseEntity.ok(updatedUser);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 上传/修改用户头像
     */
    @PostMapping("/{userId:\\d+}/avatar")
    public ResponseEntity<?> uploadAvatar(
            @NonNull @PathVariable Long userId,
            @NonNull @RequestParam("file") MultipartFile file,
            @NonNull @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long operatorUserId = getUserIdFromToken(token);
            User updatedUser = userService.updateAvatar(userId, file, operatorUserId);
            return ResponseEntity.ok(Map.of(
                    "message", "头像上传成功",
                    "avatarUrl", updatedUser.getAvatarUrl()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 获取用户头像URL
     */
    @GetMapping("/{userId:\\d+}/avatar")
    public ResponseEntity<?> getAvatarUrl(@NonNull @PathVariable Long userId) {
        try {
            String avatarUrl = userService.getAvatarUrl(userId);
            if (avatarUrl == null || avatarUrl.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "用户暂未设置头像"));
            }
            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 关注用户
     */
    @PostMapping("/{id:\\d+}/follow")
    public ResponseEntity<?> followUser(
            @NonNull @PathVariable Long id,
            @NonNull @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long userId = getUserIdFromToken(token);
            feedService.follow(userId, id);
            return ResponseEntity.ok(Map.of("message", "关注成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 取消关注用户
     */
    @DeleteMapping("/{id:\\d+}/follow")
    public ResponseEntity<?> unfollowUser(
            @NonNull @PathVariable Long id,
            @NonNull @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
            }
            Long userId = getUserIdFromToken(token);
            feedService.unfollow(userId, id);
            return ResponseEntity.ok(Map.of("message", "取消关注成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 获取用户粉丝数
     */
    @GetMapping("/{id:\\d+}/followers")
    public ResponseEntity<?> getFollowerCount(@NonNull @PathVariable Long id) {
        long count = feedService.getFollowerCount(id);
        return ResponseEntity.ok(Map.of("followerCount", count));
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }
}
