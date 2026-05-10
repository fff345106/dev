package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
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
import com.example.hello.dto.LoginRequest;
import com.example.hello.dto.RegisterRequest;
import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserRepository;
import com.example.hello.util.JwtUtil;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private InvitationCodeService invitationCodeService;

    @Mock
    private AppRegistrationCallbackService appRegistrationCallbackService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil, invitationCodeService,
                appRegistrationCallbackService);
    }

    @Test
    void register_shouldKeepContractAndCreateUser() {
        RegisterRequest request = buildRegisterRequest();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(invitationCodeService.consumeCode("123456")).thenReturn(InvitationCodeService.CodeConsumeResult.local());
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
        verifyNoInteractions(appRegistrationCallbackService);
    }

    @Test
    void register_shouldNotifyAppWhenCodeComesFromApp() {
        RegisterRequest request = buildRegisterRequest();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(invitationCodeService.consumeCode("123456")).thenReturn(InvitationCodeService.CodeConsumeResult.app());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        AuthResponse response = authService.register(request);

        assertNull(response.getToken());
        assertEquals("注册成功", response.getMessage());
        verify(appRegistrationCallbackService).notifyRegisterSuccess("123456", "10", "alice", null);
    }

    @Test
    void register_shouldPropagateRegistrationCodeFailureWithoutSavingUser() {
        RegisterRequest request = buildRegisterRequest();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(invitationCodeService.consumeCode("123456")).thenThrow(new RuntimeException("邀请码或剪艺码无效"));

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
        verifyNoInteractions(invitationCodeService, appRegistrationCallbackService);
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
        verifyNoInteractions(invitationCodeService, appRegistrationCallbackService);
    }

    @Test
    void forgotPassword_shouldRejectUnknownUser() {
        ForgotPasswordRequest request = buildForgotPasswordRequest();
        when(userRepository.findByUsername("alice")).thenReturn(java.util.Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.forgotPassword(request));

        assertEquals("用户不存在", ex.getMessage());
        verifyNoInteractions(invitationCodeService, passwordEncoder, appRegistrationCallbackService);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void forgotPassword_shouldRejectPasswordMismatchBeforeLookup() {
        ForgotPasswordRequest request = buildForgotPasswordRequest();
        request.setConfirmPassword("differentPassword");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.forgotPassword(request));

        assertEquals("两次密码不一致", ex.getMessage());
        verifyNoInteractions(userRepository, invitationCodeService, passwordEncoder, appRegistrationCallbackService);
    }

    @Test
    void register_shouldSetRegularUserRoleWhenRoleTypeProvided() {
        RegisterRequest request = buildRegisterRequest();
        request.setRoleType("REGULAR_USER");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(invitationCodeService.consumeCode("123456")).thenReturn(InvitationCodeService.CodeConsumeResult.local());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.register(request);

        assertNull(response.getToken());
        assertEquals("注册成功", response.getMessage());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(UserRole.REGULAR_USER, userCaptor.getValue().getRole());
    }

    @Test
    void register_shouldSetEnterpriseUserRoleWhenRoleTypeProvided() {
        RegisterRequest request = buildRegisterRequest();
        request.setRoleType("ENTERPRISE_USER");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(invitationCodeService.consumeCode("123456")).thenReturn(InvitationCodeService.CodeConsumeResult.local());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.register(request);

        assertNull(response.getToken());
        assertEquals("注册成功", response.getMessage());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(UserRole.ENTERPRISE_USER, userCaptor.getValue().getRole());
    }

    @Test
    void register_shouldSetMasterArtisanRoleWhenRoleTypeProvided() {
        RegisterRequest request = buildRegisterRequest();
        request.setRoleType("MASTER_ARTISAN");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(invitationCodeService.consumeCode("123456")).thenReturn(InvitationCodeService.CodeConsumeResult.local());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.register(request);

        assertNull(response.getToken());
        assertEquals("注册成功", response.getMessage());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(UserRole.MASTER_ARTISAN, userCaptor.getValue().getRole());
    }

    @Test
    void register_shouldRejectInvalidRoleType() {
        RegisterRequest request = buildRegisterRequest();
        request.setRoleType("INVALID_ROLE");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(invitationCodeService.consumeCode("123456")).thenReturn(InvitationCodeService.CodeConsumeResult.local());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));

        assertEquals("无效的角色类型", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_shouldRejectAdminRoleType() {
        RegisterRequest request = buildRegisterRequest();
        request.setRoleType("ADMIN");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(invitationCodeService.consumeCode("123456")).thenReturn(InvitationCodeService.CodeConsumeResult.local());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));

        assertEquals("不支持的角色类型", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_shouldRejectSuperAdminRoleType() {
        RegisterRequest request = buildRegisterRequest();
        request.setRoleType("SUPER_ADMIN");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(invitationCodeService.consumeCode("123456")).thenReturn(InvitationCodeService.CodeConsumeResult.local());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));

        assertEquals("不支持的角色类型", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_shouldRejectPasswordMismatch() {
        RegisterRequest request = buildRegisterRequest();
        request.setConfirmPassword("differentPassword");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));

        assertEquals("两次密码不一致", ex.getMessage());
        verifyNoInteractions(userRepository, invitationCodeService, passwordEncoder, appRegistrationCallbackService);
    }

    @Test
    void register_shouldDefaultToUserRoleWhenRoleTypeIsBlank() {
        RegisterRequest request = buildRegisterRequest();
        request.setRoleType("   ");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(invitationCodeService.consumeCode("123456")).thenReturn(InvitationCodeService.CodeConsumeResult.local());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(UserRole.USER, userCaptor.getValue().getRole());
    }

    // ==================== 登录测试 ====================

    @Test
    void login_shouldReturnTokenWhenCredentialsCorrect() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password123");

        User user = new User("alice", "encoded-password", UserRole.USER);
        user.setId(1L);
        when(userRepository.findByUsername("alice")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "alice", "USER")).thenReturn("jwt-token-abc");

        AuthResponse response = authService.login(request);

        assertEquals("jwt-token-abc", response.getToken());
        assertEquals("登录成功", response.getMessage());
    }

    @Test
    void login_shouldPassCorrectClaimsToJwtUtil() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        User user = new User("admin", "encoded", UserRole.SUPER_ADMIN);
        user.setId(42L);
        when(userRepository.findByUsername("admin")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("admin123", "encoded")).thenReturn(true);
        when(jwtUtil.generateToken(42L, "admin", "SUPER_ADMIN")).thenReturn("token");

        authService.login(request);

        verify(jwtUtil).generateToken(42L, "admin", "SUPER_ADMIN");
    }

    @Test
    void login_shouldThrowWhenUserNotFound() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nobody");
        request.setPassword("password123");

        when(userRepository.findByUsername("nobody")).thenReturn(java.util.Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));

        assertEquals("用户名或密码错误", ex.getMessage());
        verify(jwtUtil, never()).generateToken(any(), any(), any());
    }

    @Test
    void login_shouldThrowWhenPasswordWrong() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrongpass");

        User user = new User("alice", "encoded-password", UserRole.USER);
        user.setId(1L);
        when(userRepository.findByUsername("alice")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "encoded-password")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));

        // 与用户不存在时使用相同错误信息，防止用户枚举攻击
        assertEquals("用户名或密码错误", ex.getMessage());
        verify(jwtUtil, never()).generateToken(any(), any(), any());
    }

    // ==================== 游客登录测试 ====================

    @Test
    void guestLogin_shouldReturnTokenWithGuestRole() {
        when(jwtUtil.generateToken(eq(-1L), any(String.class), eq("GUEST")))
                .thenReturn("guest-token-xyz");

        AuthResponse response = authService.guestLogin();

        assertEquals("guest-token-xyz", response.getToken());
        assertEquals("游客登录成功", response.getMessage());
    }

    @Test
    void guestLogin_shouldUseFixedGuestIdAndRole() {
        when(jwtUtil.generateToken(eq(-1L), any(String.class), eq("GUEST")))
                .thenReturn("guest-token");

        authService.guestLogin();

        verify(jwtUtil).generateToken(eq(-1L), argThat(username -> username.startsWith("guest_")),
                eq("GUEST"));
    }

    @Test
    void guestLogin_shouldGenerateUsernameWithCorrectFormat() {
        when(jwtUtil.generateToken(eq(-1L), any(String.class), eq("GUEST")))
                .thenReturn("token");

        authService.guestLogin();

        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        verify(jwtUtil).generateToken(eq(-1L), usernameCaptor.capture(), eq("GUEST"));

        String guestUsername = usernameCaptor.getValue();
        assertTrue(guestUsername.startsWith("guest_"),
                "游客用户名应以 guest_ 开头，实际: " + guestUsername);
        assertEquals(14, guestUsername.length(),
                "游客用户名长度应为 14（guest_ + 8位UUID），实际: " + guestUsername.length());
    }

    @Test
    void guestLogin_shouldNotPersistToDatabase() {
        when(jwtUtil.generateToken(eq(-1L), any(String.class), eq("GUEST")))
                .thenReturn("token");

        authService.guestLogin();

        verifyNoInteractions(userRepository);
    }

    @Test
    void guestLogin_shouldGenerateUniqueUsernameEachTime() {
        when(jwtUtil.generateToken(eq(-1L), any(String.class), eq("GUEST")))
                .thenReturn("token");

        ArgumentCaptor<String> first = ArgumentCaptor.forClass(String.class);
        authService.guestLogin();
        verify(jwtUtil).generateToken(eq(-1L), first.capture(), eq("GUEST"));

        reset(jwtUtil);
        when(jwtUtil.generateToken(eq(-1L), any(String.class), eq("GUEST")))
                .thenReturn("token");

        ArgumentCaptor<String> second = ArgumentCaptor.forClass(String.class);
        authService.guestLogin();
        verify(jwtUtil).generateToken(eq(-1L), second.capture(), eq("GUEST"));

        assertNotEquals(first.getValue(), second.getValue(),
                "两次游客登录的用户名应不同");
    }

    @Test
    void register_shouldDefaultToUserRoleWhenNoRoleType() {
        RegisterRequest request = buildRegisterRequest();
        // roleType 不设置，保持 null
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(invitationCodeService.consumeCode("123456")).thenReturn(InvitationCodeService.CodeConsumeResult.local());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.register(request);

        assertNull(response.getToken());
        assertEquals("注册成功", response.getMessage());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(UserRole.USER, userCaptor.getValue().getRole());
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
