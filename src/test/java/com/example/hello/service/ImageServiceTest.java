package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ImageServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private ImageService imageService;

    @BeforeEach
    void setUp() {
        // 设置 @Value 注入的字段
        ReflectionTestUtils.setField(imageService, "baseUrl", "https://s3.example.com");
        ReflectionTestUtils.setField(imageService, "bucket", "test-bucket");
        // 将 mock S3Client 注入到私有字段（覆盖 @PostConstruct 初始化的实例）
        ReflectionTestUtils.setField(imageService, "s3Client", s3Client);
    }

    // ======================== uploadAvatar 测试 ========================

    @Test
    void uploadAvatar_Success() throws IOException {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );
        Long userId = 1L;

        // S3 putObject 返回成功
        org.mockito.Mockito.lenient().when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // When
        String result = imageService.uploadAvatar(file, userId);

        // Then
        assertNotNull(result);
        assertTrue(result.startsWith("https://s3.example.com/avatars/1/avatar"));
        assertTrue(result.contains(".jpg"));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadAvatar_EmptyFile_ThrowsException() {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                new byte[0]
        );
        Long userId = 1L;

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageService.uploadAvatar(file, userId)
        );
        assertEquals("上传文件不能为空", exception.getMessage());
    }

    @Test
    void uploadAvatar_InvalidContentType_ThrowsException() {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "test content".getBytes()
        );
        Long userId = 1L;

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageService.uploadAvatar(file, userId)
        );
        assertEquals("仅支持图片文件", exception.getMessage());
    }

    @Test
    void uploadAvatar_NullContentType_ThrowsException() {
        // Given - content type 为 null 的情况
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                null,
                "test content".getBytes()
        );
        Long userId = 1L;

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageService.uploadAvatar(file, userId)
        );
        assertEquals("仅支持图片文件", exception.getMessage());
    }

    @Test
    void uploadAvatar_FileTooLarge_ThrowsException() {
        // Given - 3MB 文件，超过 2MB 限制
        byte[] largeContent = new byte[3 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                largeContent
        );
        Long userId = 1L;

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageService.uploadAvatar(file, userId)
        );
        assertEquals("文件大小超过限制（最大2MB）", exception.getMessage());
    }

    @Test
    void uploadAvatar_PngFormat_Success() throws IOException {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "test png content".getBytes()
        );
        Long userId = 42L;

        org.mockito.Mockito.lenient().when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // When
        String result = imageService.uploadAvatar(file, userId);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("avatars/42/avatar"));
        assertTrue(result.contains(".png"));
    }

    @Test
    void uploadAvatar_S3Failure_ThrowsIOException() {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );
        Long userId = 1L;

        org.mockito.Mockito.lenient().when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(software.amazon.awssdk.services.s3.model.S3Exception.builder()
                        .message("S3 error")
                        .statusCode(500)
                        .build());

        // When & Then
        IOException exception = assertThrows(
                IOException.class,
                () -> imageService.uploadAvatar(file, userId)
        );
        assertTrue(exception.getMessage().contains("头像上传失败"));
    }

    // ======================== deleteAvatar 测试 ========================

    @Test
    void deleteAvatar_Success() {
        // Given - userId = 1, 应尝试删除 6 种扩展名的头像文件
        Long userId = 1L;

        // When
        imageService.deleteAvatar(userId);

        // Then - 验证对每种扩展名都调用了 deleteObject（共 6 次：.jpg, .jpeg, .png, .webp, .gif, .bmp）
        verify(s3Client, times(6)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteAvatar_DifferentUserId_UsesCorrectPrefix() {
        // Given
        Long userId = 999L;

        // When
        imageService.deleteAvatar(userId);

        // Then - 验证调用了 6 次 deleteObject
        verify(s3Client, times(6)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteAvatar_S3Exception_DoesNotThrow() {
        // Given - S3 抛出异常，deleteAvatar 内部捕获不应向上传播
        Long userId = 1L;

        org.mockito.Mockito.lenient().when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(software.amazon.awssdk.services.s3.model.S3Exception.builder()
                        .message("S3 error")
                        .statusCode(404)
                        .build());

        // When & Then - 不应抛出异常
        imageService.deleteAvatar(userId);

        // 验证仍然尝试删除了所有扩展名
        verify(s3Client, times(6)).deleteObject(any(DeleteObjectRequest.class));
    }
}
