package com.example.hello.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.exception.GlobalExceptionHandler;
import com.example.hello.service.InvitationCodeService;
import com.example.hello.service.UserService;
import com.example.hello.util.JwtUtil;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private InvitationCodeService invitationCodeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new UserController(userService, jwtUtil, invitationCodeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadAvatar_Success() throws Exception {
        Long userId = 1L;
        User user = new User("testuser", "password", UserRole.USER);
        user.setId(userId);
        user.setAvatarUrl("https://s3.example.com/avatars/1/avatar.jpg");

        // Controller strips "Bearer " prefix before calling jwtUtil.extractUserId()
        when(jwtUtil.extractUserId("valid-token")).thenReturn(1L);
        when(userService.updateAvatar(eq(userId), any(), eq(userId))).thenReturn(user);

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "test image content".getBytes());

        mockMvc.perform(multipart("/api/users/{userId}/avatar", userId)
                        .file(file)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("头像上传成功"))
                .andExpect(jsonPath("$.avatarUrl").value("https://s3.example.com/avatars/1/avatar.jpg"));

        verify(userService).updateAvatar(eq(userId), any(), eq(userId));
    }

    @Test
    void uploadAvatar_NoToken_Returns401() throws Exception {
        Long userId = 1L;
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "test content".getBytes());

        mockMvc.perform(multipart("/api/users/{userId}/avatar", userId).file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未提供认证令牌"));
    }

    @Test
    void getAvatarUrl_Success() throws Exception {
        Long userId = 1L;
        when(userService.getAvatarUrl(userId))
                .thenReturn("https://s3.example.com/avatars/1/avatar.jpg");

        mockMvc.perform(get("/api/users/{userId}/avatar", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl")
                        .value("https://s3.example.com/avatars/1/avatar.jpg"));

        verify(userService).getAvatarUrl(userId);
    }

    @Test
    void getAvatarUrl_NoAvatar_Returns404() throws Exception {
        Long userId = 1L;
        when(userService.getAvatarUrl(userId)).thenReturn(null);

        mockMvc.perform(get("/api/users/{userId}/avatar", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("用户暂未设置头像"));
    }

    @Test
    void uploadAvatar_PermissionDenied_Returns403() throws Exception {
        Long userId = 2L;
        when(jwtUtil.extractUserId("valid-token")).thenReturn(1L);
        when(userService.updateAvatar(eq(userId), any(), eq(1L)))
                .thenThrow(new SecurityException("无权修改该用户的头像"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "test content".getBytes());

        mockMvc.perform(multipart("/api/users/{userId}/avatar", userId)
                        .file(file)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权修改该用户的头像"));
    }

    @Test
    void getAvatarUrl_EmptyUrl_Returns404() throws Exception {
        Long userId = 1L;
        when(userService.getAvatarUrl(userId)).thenReturn("");

        mockMvc.perform(get("/api/users/{userId}/avatar", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("用户暂未设置头像"));
    }
}
