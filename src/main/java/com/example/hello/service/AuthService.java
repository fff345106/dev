package com.example.hello.service;

import com.example.hello.dto.AuthResponse;
import com.example.hello.dto.ForgotPasswordRequest;
import com.example.hello.dto.LoginRequest;
import com.example.hello.dto.RegisterRequest;
import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserRepository;
import com.example.hello.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final InvitationCodeService invitationCodeService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
            InvitationCodeService invitationCodeService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.invitationCodeService = invitationCodeService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("两次密码不一致");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        invitationCodeService.consumeCode(request.getInvitationCode());

        User user = new User(request.getUsername(), passwordEncoder.encode(request.getPassword()));
        // 默认为录入员，除非第一个注册的可能是管理员（根据业务需求调整）
        user.setRole(UserRole.USER);
        userRepository.save(user);
        return new AuthResponse(null, "注册成功");
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        return new AuthResponse(token, "登录成功");
    }

    @Transactional
    public AuthResponse forgotPassword(ForgotPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("两次密码不一致");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return new AuthResponse(null, "密码重置成功");
    }

    public AuthResponse guestLogin() {
        // 创建一个临时游客用户（如果不持久化到数据库，JWT中的ID可以是特定值或者随机生成但不存库）
        // 这里为了简单，我们生成一个不带数据库ID的Token，或者生成一个特殊的ID（例如 -1）
        // 注意：后续逻辑如果依赖数据库查用户，可能会报错。
        // 如果系统设计强依赖 userId 必须在数据库存在，那么我们需要创建一个真实的 Guest 用户。
        // 方案 A: 每次游客登录创建一个新用户（不推荐，数据库会爆）
        // 方案 B: 预设一个固定的 Guest 账号（推荐）
        // 方案 C: Token 中包含角色 GUEST，但 userId 为负数，后端逻辑需兼容
        
        // 采用方案 C: 虚拟用户
        String guestUsername = "guest_" + UUID.randomUUID().toString().substring(0, 8);
        // userId 设为 -1L 表示游客
        String token = jwtUtil.generateToken(-1L, guestUsername, UserRole.GUEST.name());
        return new AuthResponse(token, "游客登录成功");
    }
}
