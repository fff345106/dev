# 用户头像功能实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为系统添加用户头像功能，支持用户上传、修改和获取个人头像

**Architecture:** 扩展现有ImageService和UserService，在User实体添加avatarUrl字段，通过S3对象存储头像文件，实现细粒度的权限控制

**Tech Stack:** Spring Boot, Spring Data JPA, AWS S3 SDK, MySQL

---

## 文件结构映射

### 需要修改的文件

| 文件路径 | 修改内容 | 行号范围 |
|---------|---------|---------|
| `src/main/java/com/example/hello/entity/User.java` | 添加avatarUrl字段和getter/setter | 39-70 |
| `src/main/java/com/example/hello/service/ImageService.java` | 添加uploadAvatar和deleteAvatar方法 | 40-534 |
| `src/main/java/com/example/hello/service/UserService.java` | 添加updateAvatar和getAvatarUrl方法 | - |
| `src/main/java/com/example/hello/controller/UserController.java` | 添加头像上传和获取端点 | 162-163 |

### 需要创建的测试文件

| 文件路径 | 用途 |
|---------|------|
| `src/test/java/com/example/hello/service/ImageServiceTest.java` | ImageService头像方法测试 |
| `src/test/java/com/example/hello/service/UserServiceTest.java` | UserService头像方法测试 |
| `src/test/java/com/example/hello/controller/UserControllerTest.java` | UserController头像端点测试 |

---

## Task 1: 数据库变更 - 添加avatar_url字段

**Files:**
- Modify: `src/main/java/com/example/hello/entity/User.java:39-70`

- [ ] **Step 1: 创建数据库迁移脚本**

创建文件 `src/main/resources/db/migration/V2__add_avatar_url_to_users.sql`:

```sql
-- 为users表添加头像URL字段
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(500) DEFAULT NULL COMMENT '用户头像URL';
```

- [ ] **Step 2: 执行数据库迁移**

```bash
# 如果使用Flyway
mvn flyway:migrate

# 或手动执行SQL
mysql -u username -p database_name < src/main/resources/db/migration/V2__add_avatar_url_to_users.sql
```

- [ ] **Step 3: 验证字段添加成功**

```sql
DESCRIBE users;
```

预期输出应包含：
```
| avatar_url | varchar(500) | YES | | NULL | |
```

- [ ] **Step 4: 提交数据库变更**

```bash
git add src/main/resources/db/migration/V2__add_avatar_url_to_users.sql
git commit -m "feat: add avatar_url column to users table"
```

---

## Task 2: 修改User实体 - 添加avatarUrl字段

**Files:**
- Modify: `src/main/java/com/example/hello/entity/User.java:39-70`

- [ ] **Step 1: 添加avatarUrl字段**

在User.java的第39行（`private LocalDateTime createdAt;`之后）添加：

```java
@Column(name = "avatar_url")
private String avatarUrl;
```

- [ ] **Step 2: 添加getter和setter方法**

在User.java的第68行（`public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }`之后）添加：

```java
public String getAvatarUrl() { return avatarUrl; }
public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
```

- [ ] **Step 3: 验证实体编译**

```bash
mvn compile -pl . -am
```

预期：编译成功，无错误

- [ ] **Step 4: 提交实体变更**

```bash
git add src/main/java/com/example/hello/entity/User.java
git commit -m "feat: add avatarUrl field to User entity"
```

---

## Task 3: 扩展ImageService - 添加头像上传方法

**Files:**
- Modify: `src/main/java/com/example/hello/service/ImageService.java:40-534`

- [ ] **Step 1: 添加头像相关常量**

在ImageService.java的第41行（`private static final String TEMP_FOLDER = "temp/";`之后）添加：

```java
private static final String AVATAR_FOLDER = "avatars/";
private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024; // 2MB
```

- [ ] **Step 2: 添加uploadAvatar方法**

在ImageService.java的第91行（`upload`方法之后）添加：

```java
/**
 * 上传用户头像到S3
 * @param file 图片文件
 * @param userId 用户ID
 * @return 头像URL
 */
public String uploadAvatar(MultipartFile file, Long userId) throws IOException {
    // 1. 验证文件不为空
    if (file.isEmpty()) {
        throw new IllegalArgumentException("上传文件不能为空");
    }

    // 2. 验证文件类型
    String contentType = file.getContentType();
    if (contentType == null || !contentType.startsWith("image/")) {
        throw new IllegalArgumentException("仅支持图片文件");
    }

    // 3. 验证文件大小（2MB）
    if (file.getSize() > MAX_AVATAR_BYTES) {
        throw new IllegalArgumentException("文件大小超过限制（最大2MB）");
    }

    // 4. 生成S3路径：avatars/{userId}/avatar.{ext}
    String extension = resolveUploadExtension(file.getOriginalFilename(), contentType);
    String key = AVATAR_FOLDER + userId + "/avatar" + extension;

    // 5. 上传到S3
    File tempFile = File.createTempFile("avatar_", extension);
    file.transferTo(tempFile);

    try {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromFile(tempFile));
        return baseUrl + "/" + key;
    } catch (S3Exception e) {
        throw new IOException("头像上传失败: " + e.getMessage(), e);
    } finally {
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }
}
```

- [ ] **Step 3: 验证编译**

```bash
mvn compile -pl . -am
```

预期：编译成功，无错误

- [ ] **Step 4: 提交变更**

```bash
git add src/main/java/com/example/hello/service/ImageService.java
git commit -m "feat: add uploadAvatar method to ImageService"
```

---

## Task 4: 扩展ImageService - 添加头像删除方法

**Files:**
- Modify: `src/main/java/com/example/hello/service/ImageService.java:289-304`

- [ ] **Step 1: 添加deleteAvatar方法**

在ImageService.java的第289行（`deleteTempImage`方法之前）添加：

```java
/**
 * 删除用户头像
 * @param userId 用户ID
 */
public void deleteAvatar(Long userId) {
    try {
        // 列出并删除该用户的所有头像文件
        String prefix = AVATAR_FOLDER + userId + "/";
        // 尝试删除常见扩展名的头像文件
        String[] extensions = {".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"};
        for (String ext : extensions) {
            String key = prefix + "avatar" + ext;
            try {
                DeleteObjectRequest request = DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build();
                s3Client.deleteObject(request);
            } catch (S3Exception ignored) {
                // 文件不存在时忽略
            }
        }
    } catch (Exception e) {
        System.err.println("删除头像失败: " + e.getMessage());
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -pl . -am
```

预期：编译成功，无错误

- [ ] **Step 3: 提交变更**

```bash
git add src/main/java/com/example/hello/service/ImageService.java
git commit -m "feat: add deleteAvatar method to ImageService"
```

---

## Task 5: 扩展UserService - 添加头像更新方法

**Files:**
- Modify: `src/main/java/com/example/hello/service/UserService.java`

- [ ] **Step 1: 添加ImageService依赖**

在UserService.java的类定义中添加ImageService依赖（如果尚未存在）：

```java
private final UserRepository userRepository;
private final PatternDraftRepository patternDraftRepository;
private final ImageService imageService;

public UserService(UserRepository userRepository, 
                   PatternDraftRepository patternDraftRepository,
                   ImageService imageService) {
    this.userRepository = userRepository;
    this.patternDraftRepository = patternDraftRepository;
    this.imageService = imageService;
}
```

- [ ] **Step 2: 添加updateAvatar方法**

在UserService.java中添加以下方法：

```java
/**
 * 更新用户头像
 * @param userId 目标用户ID
 * @param file 头像文件
 * @param operatorUserId 操作者用户ID
 * @return 更新后的用户对象
 */
@Transactional
public User updateAvatar(Long userId, MultipartFile file, Long operatorUserId) {
    // 1. 验证目标用户存在
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

    // 2. 验证操作权限
    if (!operatorUserId.equals(userId)) {
        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new RuntimeException("操作者不存在"));
        if (operator.getRole() != UserRole.SUPER_ADMIN) {
            throw new SecurityException("无权修改该用户的头像");
        }
    }

    try {
        // 3. 删除旧头像
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            imageService.deleteAvatar(userId);
        }

        // 4. 上传新头像
        String avatarUrl = imageService.uploadAvatar(file, userId);

        // 5. 更新用户记录
        user.setAvatarUrl(avatarUrl);
        return userRepository.save(user);
    } catch (IOException e) {
        throw new RuntimeException("头像上传失败: " + e.getMessage(), e);
    }
}
```

- [ ] **Step 3: 验证编译**

```bash
mvn compile -pl . -am
```

预期：编译成功，无错误

- [ ] **Step 4: 提交变更**

```bash
git add src/main/java/com/example/hello/service/UserService.java
git commit -m "feat: add updateAvatar method to UserService"
```

---

## Task 6: 扩展UserService - 添加头像获取方法

**Files:**
- Modify: `src/main/java/com/example/hello/service/UserService.java`

- [ ] **Step 1: 添加getAvatarUrl方法**

在UserService.java中添加以下方法：

```java
/**
 * 获取用户头像URL
 * @param userId 用户ID
 * @return 头像URL，如果未设置则返回null
 */
public String getAvatarUrl(Long userId) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    return user.getAvatarUrl();
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -pl . -am
```

预期：编译成功，无错误

- [ ] **Step 3: 提交变更**

```bash
git add src/main/java/com/example/hello/service/UserService.java
git commit -m "feat: add getAvatarUrl method to UserService"
```

---

## Task 7: 扩展UserController - 添加头像上传端点

**Files:**
- Modify: `src/main/java/com/example/hello/controller/UserController.java:162-163`

- [ ] **Step 1: 添加MultipartFile导入**

在UserController.java的导入部分添加：

```java
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
```

- [ ] **Step 2: 添加uploadAvatar端点**

在UserController.java的第156行（`updateUsername`方法之后）添加：

```java
/**
 * 上传/修改用户头像
 */
@PostMapping("/{userId:\\d+}/avatar")
public ResponseEntity<?> uploadAvatar(
        @PathVariable Long userId,
        @RequestParam("file") MultipartFile file,
        @RequestHeader(value = "Authorization", required = false) String token) {
    try {
        if (token == null || token.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
        }
        Long operatorUserId = getUserIdFromToken(token);
        User updatedUser = userService.updateAvatar(userId, file, operatorUserId);
        return ResponseEntity.ok(Map.of(
                "message", "头像上传成功",
                "avatarUrl", updatedUser.getAvatarUrl()
        ));
    } catch (SecurityException e) {
        return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
```

- [ ] **Step 3: 验证编译**

```bash
mvn compile -pl . -am
```

预期：编译成功，无错误

- [ ] **Step 4: 提交变更**

```bash
git add src/main/java/com/example/hello/controller/UserController.java
git commit -m "feat: add avatar upload endpoint to UserController"
```

---

## Task 8: 扩展UserController - 添加头像获取端点

**Files:**
- Modify: `src/main/java/com/example/hello/controller/UserController.java`

- [ ] **Step 1: 添加getAvatarUrl端点**

在UserController.java的`uploadAvatar`方法之后添加：

```java
/**
 * 获取用户头像URL
 */
@GetMapping("/{userId:\\d+}/avatar")
public ResponseEntity<?> getAvatarUrl(@PathVariable Long userId) {
    try {
        String avatarUrl = userService.getAvatarUrl(userId);
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "用户暂未设置头像"));
        }
        return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -pl . -am
```

预期：编译成功，无错误

- [ ] **Step 3: 提交变更**

```bash
git add src/main/java/com/example/hello/controller/UserController.java
git commit -m "feat: add avatar URL retrieval endpoint to UserController"
```

---

## Task 9: 编写ImageService单元测试

**Files:**
- Create: `src/test/java/com/example/hello/service/ImageServiceTest.java`

- [ ] **Step 1: 创建测试类**

创建文件 `src/test/java/com/example/hello/service/ImageServiceTest.java`:

```java
package com.example.hello.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private ImageService imageService;

    @Test
    void uploadAvatar_Success() throws IOException {
        // Given
        ReflectionTestUtils.setField(imageService, "baseUrl", "https://s3.example.com");
        ReflectionTestUtils.setField(imageService, "bucket", "test-bucket");
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );
        Long userId = 1L;

        when(s3Client.putObject(any(PutObjectRequest.class), any()))
                .thenReturn(PutObjectResponse.builder().build());

        // When
        String result = imageService.uploadAvatar(file, userId);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("avatars/1/avatar"));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any());
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
    void uploadAvatar_FileTooLarge_ThrowsException() {
        // Given
        byte[] largeContent = new byte[3 * 1024 * 1024]; // 3MB
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
}
```

- [ ] **Step 2: 运行测试**

```bash
mvn test -Dtest=ImageServiceTest -pl .
```

预期：所有测试通过

- [ ] **Step 3: 提交测试**

```bash
git add src/test/java/com/example/hello/service/ImageServiceTest.java
git commit -m "test: add unit tests for ImageService avatar methods"
```

---

## Task 10: 编写UserService单元测试

**Files:**
- Create: `src/test/java/com/example/hello/service/UserServiceTest.java`

- [ ] **Step 1: 创建测试类**

创建文件 `src/test/java/com/example/hello/service/UserServiceTest.java`:

```java
package com.example.hello.service;

import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.PatternDraftRepository;
import com.example.hello.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatternDraftRepository patternDraftRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void updateAvatar_Success() throws IOException {
        // Given
        Long userId = 1L;
        Long operatorUserId = 1L;
        User user = new User("testuser", "password", UserRole.USER);
        user.setId(userId);
        user.setAvatarUrl(null);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(imageService.uploadAvatar(file, userId)).thenReturn("https://s3.example.com/avatars/1/avatar.jpg");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        User result = userService.updateAvatar(userId, file, operatorUserId);

        // Then
        assertNotNull(result);
        assertEquals("https://s3.example.com/avatars/1/avatar.jpg", result.getAvatarUrl());
        verify(imageService, never()).deleteAvatar(userId);
        verify(imageService, times(1)).uploadAvatar(file, userId);
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void updateAvatar_ReplaceExistingAvatar_Success() throws IOException {
        // Given
        Long userId = 1L;
        Long operatorUserId = 1L;
        User user = new User("testuser", "password", UserRole.USER);
        user.setId(userId);
        user.setAvatarUrl("https://s3.example.com/avatars/1/avatar.jpg");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "new-avatar.jpg",
                "image/jpeg",
                "new image content".getBytes()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(imageService.uploadAvatar(file, userId)).thenReturn("https://s3.example.com/avatars/1/avatar_new.jpg");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        User result = userService.updateAvatar(userId, file, operatorUserId);

        // Then
        assertNotNull(result);
        verify(imageService, times(1)).deleteAvatar(userId);
        verify(imageService, times(1)).uploadAvatar(file, userId);
    }

    @Test
    void updateAvatar_UserNotFound_ThrowsException() {
        // Given
        Long userId = 999L;
        Long operatorUserId = 1L;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "test content".getBytes()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> userService.updateAvatar(userId, file, operatorUserId)
        );
        assertEquals("用户不存在", exception.getMessage());
    }

    @Test
    void updateAvatar_PermissionDenied_ThrowsException() {
        // Given
        Long userId = 2L;
        Long operatorUserId = 1L;
        User targetUser = new User("targetuser", "password", UserRole.USER);
        targetUser.setId(userId);
        User operatorUser = new User("operator", "password", UserRole.USER);
        operatorUser.setId(operatorUserId);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "test content".getBytes()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(operatorUserId)).thenReturn(Optional.of(operatorUser));

        // When & Then
        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> userService.updateAvatar(userId, file, operatorUserId)
        );
        assertEquals("无权修改该用户的头像", exception.getMessage());
    }

    @Test
    void getAvatarUrl_Success() {
        // Given
        Long userId = 1L;
        User user = new User("testuser", "password", UserRole.USER);
        user.setId(userId);
        user.setAvatarUrl("https://s3.example.com/avatars/1/avatar.jpg");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        String result = userService.getAvatarUrl(userId);

        // Then
        assertEquals("https://s3.example.com/avatars/1/avatar.jpg", result);
    }

    @Test
    void getAvatarUrl_NoAvatar_ReturnsNull() {
        // Given
        Long userId = 1L;
        User user = new User("testuser", "password", UserRole.USER);
        user.setId(userId);
        user.setAvatarUrl(null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        String result = userService.getAvatarUrl(userId);

        // Then
        assertNull(result);
    }

    @Test
    void getAvatarUrl_UserNotFound_ThrowsException() {
        // Given
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> userService.getAvatarUrl(userId)
        );
        assertEquals("用户不存在", exception.getMessage());
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
mvn test -Dtest=UserServiceTest -pl .
```

预期：所有测试通过

- [ ] **Step 3: 提交测试**

```bash
git add src/test/java/com/example/hello/service/UserServiceTest.java
git commit -m "test: add unit tests for UserService avatar methods"
```

---

## Task 11: 编写UserController集成测试

**Files:**
- Create: `src/test/java/com/example/hello/controller/UserControllerTest.java`

- [ ] **Step 1: 创建测试类**

创建文件 `src/test/java/com/example/hello/controller/UserControllerTest.java`:

```java
package com.example.hello.controller;

import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.service.UserService;
import com.example.hello.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    @WithMockUser
    void uploadAvatar_Success() throws Exception {
        // Given
        Long userId = 1L;
        User user = new User("testuser", "password", UserRole.USER);
        user.setId(userId);
        user.setAvatarUrl("https://s3.example.com/avatars/1/avatar.jpg");

        when(jwtUtil.extractUserId("valid-token")).thenReturn(1L);
        when(userService.updateAvatar(eq(userId), any(), eq(1L))).thenReturn(user);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        // When & Then
        mockMvc.perform(multipart("/api/users/{userId}/avatar", userId)
                        .file(file)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("头像上传成功"))
                .andExpect(jsonPath("$.avatarUrl").value("https://s3.example.com/avatars/1/avatar.jpg"));
    }

    @Test
    @WithMockUser
    void uploadAvatar_NoToken_Returns401() throws Exception {
        // Given
        Long userId = 1L;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "test content".getBytes()
        );

        // When & Then
        mockMvc.perform(multipart("/api/users/{userId}/avatar", userId)
                        .file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未提供认证令牌"));
    }

    @Test
    @WithMockUser
    void getAvatarUrl_Success() throws Exception {
        // Given
        Long userId = 1L;
        when(jwtUtil.extractUserId("valid-token")).thenReturn(1L);
        when(userService.getAvatarUrl(userId)).thenReturn("https://s3.example.com/avatars/1/avatar.jpg");

        // When & Then
        mockMvc.perform(get("/api/users/{userId}/avatar", userId)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("https://s3.example.com/avatars/1/avatar.jpg"));
    }

    @Test
    @WithMockUser
    void getAvatarUrl_NoAvatar_Returns404() throws Exception {
        // Given
        Long userId = 1L;
        when(jwtUtil.extractUserId("valid-token")).thenReturn(1L);
        when(userService.getAvatarUrl(userId)).thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/users/{userId}/avatar", userId)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("用户暂未设置头像"));
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
mvn test -Dtest=UserControllerTest -pl .
```

预期：所有测试通过

- [ ] **Step 3: 提交测试**

```bash
git add src/test/java/com/example/hello/controller/UserControllerTest.java
git commit -m "test: add integration tests for UserController avatar endpoints"
```

---

## Task 12: 运行完整测试套件

**Files:**
- None (运行所有测试)

- [ ] **Step 1: 运行所有测试**

```bash
mvn test
```

预期：所有测试通过，无失败

- [ ] **Step 2: 检查测试覆盖率（可选）**

```bash
mvn jacoco:report
```

查看 `target/site/jacoco/index.html` 中的覆盖率报告

- [ ] **Step 3: 提交最终变更**

```bash
git add .
git commit -m "feat: complete user avatar functionality implementation"
```

---

## 自审清单

### 1. 规格覆盖检查

✅ **数据模型变更**：Task 1-2 实现了User实体的avatarUrl字段

✅ **API接口设计**：Task 7-8 实现了头像上传和获取端点

✅ **权限控制**：Task 5 实现了用户自己+超级管理员的权限验证

✅ **存储规格**：Task 3 实现了2MB限制和S3存储路径

✅ **错误处理**：Task 7-8 实现了完整的错误响应

✅ **自动清理**：Task 4 实现了旧头像删除功能

### 2. 占位符扫描

✅ 无"TBD"、"TODO"、"实现后补充"等占位符

✅ 所有代码步骤都包含完整的实现代码

✅ 所有测试步骤都包含完整的测试代码

### 3. 类型一致性检查

✅ 方法签名一致：`uploadAvatar(MultipartFile file, Long userId)`

✅ 方法签名一致：`deleteAvatar(Long userId)`

✅ 方法签名一致：`updateAvatar(Long userId, MultipartFile file, Long operatorUserId)`

✅ 方法签名一致：`getAvatarUrl(Long userId)`

✅ 字段名称一致：`avatarUrl`

✅ 常量名称一致：`AVATAR_FOLDER`, `MAX_AVATAR_BYTES`

---

## 执行选项

**Plan complete and saved to `docs/superpowers/plans/2026-05-05-user-avatar.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
