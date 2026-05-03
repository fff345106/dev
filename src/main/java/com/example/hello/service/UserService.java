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
}
