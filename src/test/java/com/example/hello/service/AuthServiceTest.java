package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.hello.dto.AuthResponse;
import com.example.hello.dto.ForgotPasswordRequest;
import com.example.hello.dto.RegisterRequest;
import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserRepository;
import com.example.hello.util.JwtUtil;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private InvitationCodeService invitationCodeService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil, invitationCodeService);
    }

    @Test
    void register_shouldKeepContractAndCreateUser() {
        RegisterRequest request = buildRegisterRequest();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.register(request);

        assertNull(response.getToken());
        assertEquals("注册成功", response.getMessage());
        verify(invitationCodeService).consumeCode("123456");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("alice", savedUser.getUsername());
        assertEquals("encoded-password", savedUser.getPassword());
        assertEquals(UserRole.USER, savedUser.getRole());
    }

    @Test
    void register_shouldPropagateRegistrationCodeFailureWithoutSavingUser() {
        RegisterRequest request = buildRegisterRequest();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        doThrow(new RuntimeException("邀请码或剪艺码无效")).when(invitationCodeService).consumeCode("123456");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));

        assertEquals("邀请码或剪艺码无效", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_shouldRejectDuplicateUsernameBeforeConsumingCode() {
        RegisterRequest request = buildRegisterRequest();
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));

        assertEquals("用户名已存在", ex.getMessage());
        verifyNoInteractions(invitationCodeService);
    }

    @Test
    void forgotPassword_shouldResetPasswordWhenUserExists() {
        ForgotPasswordRequest request = buildForgotPasswordRequest();
        User user = new User("alice", "old-password");
        when(userRepository.findByUsername("alice")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("encoded-new-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.forgotPassword(request);

        assertNull(response.getToken());
        assertEquals("密码重置成功", response.getMessage());
        assertEquals("encoded-new-password", user.getPassword());
        verify(userRepository).save(user);
        verifyNoInteractions(invitationCodeService);
    }

    @Test
    void forgotPassword_shouldRejectUnknownUser() {
        ForgotPasswordRequest request = buildForgotPasswordRequest();
        when(userRepository.findByUsername("alice")).thenReturn(java.util.Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.forgotPassword(request));

        assertEquals("用户不存在", ex.getMessage());
        verifyNoInteractions(invitationCodeService, passwordEncoder);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void forgotPassword_shouldRejectPasswordMismatchBeforeLookup() {
        ForgotPasswordRequest request = buildForgotPasswordRequest();
        request.setConfirmPassword("differentPassword");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.forgotPassword(request));

        assertEquals("两次密码不一致", ex.getMessage());
        verifyNoInteractions(userRepository, invitationCodeService, passwordEncoder);
    }

    private RegisterRequest buildRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setInvitationCode("123456");
        return request;
    }

    private ForgotPasswordRequest buildForgotPasswordRequest() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setUsername("alice");
        request.setNewPassword("newPassword123");
        request.setConfirmPassword("newPassword123");
        return request;
    }
}
