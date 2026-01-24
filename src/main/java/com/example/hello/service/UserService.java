package com.example.hello.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.PatternDraftRepository;
import com.example.hello.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PatternDraftRepository draftRepository;

    public UserService(UserRepository userRepository, PatternDraftRepository draftRepository) {
        this.userRepository = userRepository;
        this.draftRepository = draftRepository;
    }

    /**
     * 删除用户账号
     * @param targetUserId 要删除的用户ID
     * @param operatorUserId 操作者用户ID
     */
    @Transactional
    public void deleteUser(Long targetUserId, Long operatorUserId) {
        // 获取目标用户
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 获取操作者
        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new RuntimeException("操作者不存在"));

        // 权限检查：只有超级管理员可以删除其他用户，用户可以删除自己
        boolean isSuperAdmin = operator.getRole() == UserRole.SUPER_ADMIN;
        boolean isSelf = targetUserId.equals(operatorUserId);

        if (!isSuperAdmin && !isSelf) {
            throw new RuntimeException("无权限删除该用户");
        }

        // 不允许删除超级管理员
        if (targetUser.getRole() == UserRole.SUPER_ADMIN) {
            throw new RuntimeException("不允许删除超级管理员账号");
        }

        // 删除该用户的所有草稿
        draftRepository.deleteAll(draftRepository.findByUserIdOrderByUpdatedAtDesc(targetUserId));

        // 物理删除用户
        // 注意：待审核纹样和审核记录会保留（因为没有级联删除）
        userRepository.delete(targetUser);
    }

    /**
     * 获取用户信息
     */
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    /**
     * 获取所有用户列表（仅超级管理员可操作）
     */
    public java.util.List<User> getAllUsers(Long operatorUserId) {
        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new RuntimeException("操作者不存在"));

        if (operator.getRole() != UserRole.SUPER_ADMIN) {
            throw new RuntimeException("无权限查看用户列表");
        }

        return userRepository.findAll();
    }

    /**
     * 设置用户角色（仅超级管理员可操作）
     */
    public User setUserRole(Long targetUserId, UserRole newRole, Long operatorUserId) {
        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new RuntimeException("操作者不存在"));

        // 只有超级管理员可以设置角色
        if (operator.getRole() != UserRole.SUPER_ADMIN) {
            throw new RuntimeException("无权限设置用户角色");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 不允许修改超级管理员的角色
        if (targetUser.getRole() == UserRole.SUPER_ADMIN) {
            throw new RuntimeException("不允许修改超级管理员角色");
        }

        // 不允许将用户设置为超级管理员
        if (newRole == UserRole.SUPER_ADMIN) {
            throw new RuntimeException("不允许设置为超级管理员");
        }

        targetUser.setRole(newRole);
        return userRepository.save(targetUser);
    }
}
