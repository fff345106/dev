package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.PatternDraftRepository;
import com.example.hello.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceUpdateUsernameTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatternDraftRepository draftRepository;

    @Mock
    private RedisCacheService redisCacheService;

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

    @Test
    void updateUsername_Success() {
        // Arrange - admin modifies another user
        String newUsername = "newuser123";
        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername(newUsername)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        User result = userService.updateUsername(1L, newUsername, 2L);

        // Assert
        assertNotNull(result);
        assertEquals(newUsername, testUser.getUsername());
        verify(userRepository).save(testUser);
        verify(redisCacheService).evict("users::id:1");
    }

    @Test
    void updateUsername_SelfModification() {
        // Arrange
        String newUsername = "selfmodified";
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername(newUsername)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        User result = userService.updateUsername(1L, newUsername, 1L);

        // Assert
        assertNotNull(result);
        assertEquals(newUsername, testUser.getUsername());
    }

    @Test
    void updateUsername_AdminModification() {
        // Arrange
        String newUsername = "adminmodified";
        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername(newUsername)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        User result = userService.updateUsername(1L, newUsername, 2L);

        // Assert
        assertNotNull(result);
        assertEquals(newUsername, testUser.getUsername());
    }

    @Test
    void updateUsername_NoPermission() {
        // Arrange
        User anotherUser = new User("another", "password", UserRole.USER);
        anotherUser.setId(3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(anotherUser));

        // Act & Assert
        assertThrows(SecurityException.class, () -> {
            userService.updateUsername(1L, "newname", 3L);
        });
    }

    @Test
    void updateUsername_EmptyUsername() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            userService.updateUsername(1L, "", 1L);
        });
    }

    @Test
    void updateUsername_InvalidLength() {
        // Arrange
        String longUsername = "a".repeat(31);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            userService.updateUsername(1L, longUsername, 1L);
        });
    }

    @Test
    void updateUsername_InvalidCharacters() {
        // Arrange
        String invalidUsername = "user@name!";
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            userService.updateUsername(1L, invalidUsername, 1L);
        });
    }

    @Test
    void updateUsername_DuplicateUsername() {
        // Arrange
        String duplicateUsername = "existinguser";
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername(duplicateUsername)).thenReturn(true);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            userService.updateUsername(1L, duplicateUsername, 1L);
        });
    }

    @Test
    void updateUsername_NullUsername() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            userService.updateUsername(1L, null, 1L);
        });
    }

    @Test
    void updateUsername_OperatorNotFound() {
        // Arrange
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.updateUsername(1L, "newname", 99L);
        });
        assertEquals("操作者不存在", exception.getMessage());
    }
}
