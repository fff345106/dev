package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.PatternDraftRepository;
import com.example.hello.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatternDraftRepository draftRepository;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        testUser = new User("testuser", "password123", UserRole.USER);
        testUser.setId(1L);

        adminUser = new User("admin", "admin123", UserRole.SUPER_ADMIN);
        adminUser.setId(2L);
    }

    // ========== updateAvatar 测试 ==========

    @Test
    void updateAvatar_Success() throws IOException {
        // Arrange - 用户修改自己的头像
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "test image content".getBytes()
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(imageService.uploadAvatar(file, 1L)).thenReturn("https://s3.example.com/avatars/1/avatar.jpg");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        User result = userService.updateAvatar(1L, file, 1L);

        // Assert
        assertNotNull(result);
        assertEquals("https://s3.example.com/avatars/1/avatar.jpg", testUser.getAvatarUrl());
        verify(imageService, never()).deleteAvatar(anyLong());
        verify(imageService).uploadAvatar(file, 1L);
        verify(userRepository).save(testUser);
        verify(redisCacheService).evict("users::id:1");
    }

    @Test
    void updateAvatar_ReplaceExistingAvatar_Success() throws IOException {
        // Arrange - 用户已有头像，替换为新头像
        testUser.setAvatarUrl("https://s3.example.com/avatars/1/avatar.jpg");

        MockMultipartFile file = new MockMultipartFile(
                "file", "new-avatar.jpg", "image/jpeg", "new image content".getBytes()
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(imageService.uploadAvatar(file, 1L)).thenReturn("https://s3.example.com/avatars/1/avatar_new.jpg");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        User result = userService.updateAvatar(1L, file, 1L);

        // Assert
        assertNotNull(result);
        verify(imageService).deleteAvatar(1L);
        verify(imageService).uploadAvatar(file, 1L);
        verify(userRepository).save(testUser);
    }

    @Test
    void updateAvatar_AdminModifiesOtherUser_Success() throws IOException {
        // Arrange - 管理员修改其他用户的头像
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "test content".getBytes()
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));
        when(imageService.uploadAvatar(file, 1L)).thenReturn("https://s3.example.com/avatars/1/avatar.jpg");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        User result = userService.updateAvatar(1L, file, 2L);

        // Assert
        assertNotNull(result);
        verify(imageService).uploadAvatar(file, 1L);
    }

    @Test
    void updateAvatar_UserNotFound_ThrowsException() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "test content".getBytes()
        );
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.updateAvatar(999L, file, 1L);
        });
        assertEquals("用户不存在", exception.getMessage());
    }

    @Test
    void updateAvatar_PermissionDenied_ThrowsException() {
        // Arrange - 普通用户修改其他用户的头像
        User anotherUser = new User("another", "password", UserRole.USER);
        anotherUser.setId(3L);

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "test content".getBytes()
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(3L)).thenReturn(Optional.of(anotherUser));

        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            userService.updateAvatar(1L, file, 3L);
        });
        assertEquals("无权修改该用户的头像", exception.getMessage());
    }

    @Test
    void updateAvatar_OperatorNotFound_ThrowsException() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "test content".getBytes()
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.updateAvatar(1L, file, 99L);
        });
        assertEquals("操作者不存在", exception.getMessage());
    }

    @Test
    void updateAvatar_UploadFails_ThrowsException() throws IOException {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "test content".getBytes()
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(imageService.uploadAvatar(file, 1L)).thenThrow(new IOException("S3连接失败"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.updateAvatar(1L, file, 1L);
        });
        assertTrue(exception.getMessage().contains("头像上传失败"));
    }

    // ========== getAvatarUrl 测试 ==========

    @Test
    void getAvatarUrl_Success() {
        // Arrange
        testUser.setAvatarUrl("https://s3.example.com/avatars/1/avatar.jpg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act
        String result = userService.getAvatarUrl(1L);

        // Assert
        assertEquals("https://s3.example.com/avatars/1/avatar.jpg", result);
    }

    @Test
    void getAvatarUrl_NoAvatar_ReturnsNull() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act
        String result = userService.getAvatarUrl(1L);

        // Assert
        assertNull(result);
    }

    @Test
    void getAvatarUrl_UserNotFound_ThrowsException() {
        // Arrange
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.getAvatarUrl(999L);
        });
        assertEquals("用户不存在", exception.getMessage());
    }
}
