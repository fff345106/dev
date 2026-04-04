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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.example.hello.dto.AuthResponse;
import com.example.hello.dto.ForgotPasswordRequest;
import com.example.hello.exception.GlobalExceptionHandler;
import com.example.hello.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
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

    @Test
    void forgotPassword_shouldDelegateToService() throws Exception {
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
    void forgotPassword_shouldReturn400WhenNewPasswordTooShort() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  \"username\": \"alice\",
                                  \"newPassword\": \"12345\",
                                  \"confirmPassword\": \"12345\"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("新密码长度至少6位"));

        verifyNoInteractions(authService);
    }
}
