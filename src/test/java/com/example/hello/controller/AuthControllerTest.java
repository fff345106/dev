package com.example.hello.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.example.hello.dto.AuthResponse;
import com.example.hello.dto.ForgotPasswordRequest;
import com.example.hello.dto.LoginRequest;
import com.example.hello.dto.RegisterRequest;
import com.example.hello.exception.GlobalExceptionHandler;
import com.example.hello.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController 集成测试")
@SuppressWarnings("null")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    // ==================== 注册接口测试 ====================
    @Nested
    @DisplayName("POST /api/auth/register")
    class RegisterEndpoint {

        @Test
        @DisplayName("有效的注册请求应返回 200 和成功消息")
        void shouldReturn200WhenValidRequest() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                    .thenReturn(new AuthResponse(null, "注册成功"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "newuser",
                                      "password": "123456",
                                      "confirmPassword": "123456",
                                      "invitationCode": "123456"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").doesNotExist())
                    .andExpect(jsonPath("$.message").value("注册成功"));

            verify(authService).register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("用户名为空时应返回 400")
        void shouldReturn400WhenUsernameBlank() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "",
                                      "password": "123456",
                                      "confirmPassword": "123456",
                                      "invitationCode": "123456"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").exists());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("用户名过短时应返回 400")
        void shouldReturn400WhenUsernameTooShort() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "ab",
                                      "password": "123456",
                                      "confirmPassword": "123456",
                                      "invitationCode": "123456"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("用户名长度必须在3-20之间"));

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("密码过短时应返回 400")
        void shouldReturn400WhenPasswordTooShort() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "newuser",
                                      "password": "12345",
                                      "confirmPassword": "12345",
                                      "invitationCode": "123456"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("密码长度至少6位"));

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("邀请码格式错误时应返回 400")
        void shouldReturn400WhenInvitationCodeInvalid() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "newuser",
                                      "password": "123456",
                                      "confirmPassword": "123456",
                                      "invitationCode": "abc"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("邀请码或剪艺码必须为6位或8位数字"));

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Service 层抛出异常时应返回 400")
        void shouldReturn400WhenServiceThrows() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new RuntimeException("用户名已存在"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "existing",
                                      "password": "123456",
                                      "confirmPassword": "123456",
                                      "invitationCode": "123456"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.token").doesNotExist())
                    .andExpect(jsonPath("$.message").value("用户名已存在"));
        }
    }

    // ==================== 登录接口测试 ====================
    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginEndpoint {

        @Test
        @DisplayName("有效的登录请求应返回 200 和 JWT Token")
        void shouldReturn200WithTokenWhenValid() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenReturn(new AuthResponse("jwt-token-abc", "登录成功"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "admin",
                                      "password": "admin123"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-token-abc"))
                    .andExpect(jsonPath("$.message").value("登录成功"));

            verify(authService).login(any(LoginRequest.class));
        }

        @Test
        @DisplayName("用户名为空时应返回 400")
        void shouldReturn400WhenUsernameBlank() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "",
                                      "password": "123456"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").exists());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("密码为空时应返回 400")
        void shouldReturn400WhenPasswordBlank() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "admin",
                                      "password": ""
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").exists());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("凭证错误时应返回 400")
        void shouldReturn400WhenCredentialsWrong() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new RuntimeException("用户名或密码错误"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "admin",
                                      "password": "wrong"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("用户名或密码错误"));
        }
    }

    // ==================== 忘记密码接口测试 ====================
    @Nested
    @DisplayName("POST /api/auth/forgot-password")
    class ForgotPasswordEndpoint {

        @Test
        @DisplayName("有效的重置请求应返回 200 和成功消息")
        void shouldReturn200WhenValidRequest() throws Exception {
            when(authService.forgotPassword(any(ForgotPasswordRequest.class)))
                    .thenReturn(new AuthResponse(null, "密码重置成功"));

            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setUsername("alice");
            request.setNewPassword("newPassword123");
            request.setConfirmPassword("newPassword123");

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("密码重置成功"));

            verify(authService).forgotPassword(any(ForgotPasswordRequest.class));
        }

        @Test
        @DisplayName("新密码过短时应返回 400")
        void shouldReturn400WhenNewPasswordTooShort() throws Exception {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "alice",
                                      "newPassword": "12345",
                                      "confirmPassword": "12345"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("新密码长度至少6位"));

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("用户名为空时应返回 400")
        void shouldReturn400WhenUsernameBlank() throws Exception {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "",
                                      "newPassword": "newPass123",
                                      "confirmPassword": "newPass123"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").exists());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("用户不存在时应返回 400")
        void shouldReturn400WhenUserNotFound() throws Exception {
            when(authService.forgotPassword(any(ForgotPasswordRequest.class)))
                    .thenThrow(new RuntimeException("用户不存在"));

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "ghost",
                                      "newPassword": "newPass123",
                                      "confirmPassword": "newPass123"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("用户不存在"));
        }
    }

    // ==================== 游客登录接口测试 ====================
    @Nested
    @DisplayName("POST /api/auth/guest-login")
    class GuestLoginEndpoint {

        @Test
        @DisplayName("游客登录应返回 200 和 JWT Token")
        void shouldReturn200WithGuestToken() throws Exception {
            when(authService.guestLogin())
                    .thenReturn(new AuthResponse("guest-token-xyz", "游客登录成功"));

            mockMvc.perform(post("/api/auth/guest-login"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("guest-token-xyz"))
                    .andExpect(jsonPath("$.message").value("游客登录成功"));

            verify(authService).guestLogin();
        }

        @Test
        @DisplayName("游客登录不需要请求体")
        void shouldWorkWithoutRequestBody() throws Exception {
            when(authService.guestLogin())
                    .thenReturn(new AuthResponse("token", "游客登录成功"));

            mockMvc.perform(post("/api/auth/guest-login"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("游客登录成功"));
        }
    }
}
