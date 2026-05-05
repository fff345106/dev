package com.example.hello.service;

import java.time.Duration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.PatternDraftRepository;
import com.example.hello.repository.UserRepository;

@Service
public class UserService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final PatternDraftRepository draftRepository;
    private final RedisCacheService redisCacheService;

    public UserService(UserRepository userRepository, PatternDraftRepository draftRepository, RedisCacheService redisCacheService) {
        this.userRepository = userRepository;
        this.draftRepository = draftRepository;
        this.redisCacheService = redisCacheService;
    }

    @Transactional
    public void deleteUser(Long targetUserId, Long operatorUserId) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new RuntimeException("操作者不存在"));

        boolean isSuperAdmin = operator.getRole() == UserRole.SUPER_ADMIN;
        boolean isSelf = targetUserId.equals(operatorUserId);

        if (!isSuperAdmin && !isSelf) {
            throw new RuntimeException("无权限删除该用户");
        }

        if (targetUser.getRole() == UserRole.SUPER_ADMIN) {
            throw new RuntimeException("不允许删除超级管理员账号");
        }

        draftRepository.deleteAll(draftRepository.findByUserIdOrderByUpdatedAtDesc(targetUserId));
        userRepository.delete(targetUser);
        redisCacheService.evict("users::id:" + targetUserId);
    }

    public User getUserById(Long userId) {
        if (userId < 0) {
            User guest = new User();
            guest.setId(userId);
            guest.setUsername("Guest");
            guest.setRole(UserRole.GUEST);
            return guest;
        }
        String key = "users::id:" + userId;
        User cached = redisCacheService.get(key, User.class);
        if (cached != null) return cached;
        User result = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        redisCacheService.put(key, result, CACHE_TTL);
        return result;
    }

    public java.util.List<User> getAllUsers(Long operatorUserId) {
        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new RuntimeException("操作者不存在"));

        if (operator.getRole() != UserRole.SUPER_ADMIN) {
            throw new RuntimeException("无权限查看用户列表");
        }

        return userRepository.findAll();
    }

    public Page<User> getAllUsers(Long operatorUserId, Pageable pageable) {
        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new RuntimeException("操作者不存在"));

        if (operator.getRole() != UserRole.SUPER_ADMIN) {
            throw new RuntimeException("无权限查看用户列表");
        }

        return userRepository.findAll(pageable);
    }

    public User setUserRole(Long targetUserId, UserRole newRole, Long operatorUserId) {
        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new RuntimeException("操作者不存在"));

        if (operator.getRole() != UserRole.SUPER_ADMIN) {
            throw new RuntimeException("无权限设置用户角色");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (targetUser.getRole() == UserRole.SUPER_ADMIN) {
            throw new RuntimeException("不允许修改超级管理员角色");
        }

        if (newRole == UserRole.SUPER_ADMIN) {
            throw new RuntimeException("不允许设置为超级管理员");
        }

        targetUser.setRole(newRole);
        User saved = userRepository.save(targetUser);
        redisCacheService.evict("users::id:" + targetUserId);
        return saved;
    }

    @Transactional
    public User updateUsername(Long targetUserId, String newUsername, Long operatorUserId) {
        // 1. 验证权限
        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new RuntimeException("操作者不存在"));

        boolean isSelf = targetUserId.equals(operatorUserId);
        boolean isSuperAdmin = operator.getRole() == UserRole.SUPER_ADMIN;

        if (!isSelf && !isSuperAdmin) {
            throw new SecurityException("无权限修改该用户名");
        }

        // 2. 验证用户名格式
        if (newUsername == null || newUsername.trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (newUsername.length() > 30) {
            throw new RuntimeException("用户名长度必须在1-30个字符之间");
        }
        if (!newUsername.matches("^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$")) {
            throw new RuntimeException("用户名只能包含字母、数字、下划线和中文");
        }

        // 3. 查询目标用户
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 4. 检查用户名是否已被占用（排除自身）
        if (!targetUser.getUsername().equals(newUsername)
                && userRepository.existsByUsername(newUsername)) {
            throw new RuntimeException("用户名已被占用");
        }
        targetUser.setUsername(newUsername);
        User saved = userRepository.save(targetUser);

        // 5. 清除缓存
        redisCacheService.evict("users::id:" + targetUserId);

        return saved;
    }
}
