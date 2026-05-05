# 修改用户名功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为系统添加修改用户名的功能，支持用户修改自己的用户名和超级管理员修改任意用户的用户名。

**Architecture:** 在现有 UserController 和 UserService 中添加修改用户名的端点和业务逻辑，使用 DTO 进行参数验证，遵循现有的权限控制和缓存模式。

**Tech Stack:** Spring Boot, Spring Data JPA, Jakarta Validation, Redis Cache

---

## 文件结构

### 新增文件
- `src/main/java/com/example/hello/dto/UpdateUsernameRequest.java` - 请求 DTO，包含用户名验证规则
- `src/test/java/com/example/hello/service/UserServiceUpdateUsernameTest.java` - 单元测试

### 修改文件
- `src/main/java/com/example/hello/service/UserService.java:116` - 添加 `updateUsername` 方法
- `src/main/java/com/example/hello/controller/UserController.java:135` - 添加 `updateUsername` 端点

---

## Task 1: 创建 UpdateUsernameRequest DTO

**Files:**
- Create: `src/main/java/com/example/hello/dto/UpdateUsernameRequest.java`

- [ ] **Step 1: 创建 UpdateUsernameRequest 类**

```java
package com.example.hello.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateUsernameRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 1, max = 30, message = "用户名长度必须在1-30个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$",
             message = "用户名只能包含字母、数字、下划线和中文")
    private String username;

    public UpdateUsernameRequest() {}

    public UpdateUsernameRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
```

- [ ] **Step 2: 验证文件创建成功**

Run: `ls -la src/main/java/com/example/hello/dto/UpdateUsernameRequest.java`
Expected: 文件存在，权限正确

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/example/hello/dto/UpdateUsernameRequest.java
git commit -m "feat: 添加 UpdateUsernameRequest DTO"
```

---

## Task 2: 在 UserService 中添加 updateUsername 方法

**Files:**
- Modify: `src/main/java/com/example/hello/service/UserService.java:116`

- [ ] **Step 1: 添加 updateUsername 方法**

在 `UserService.java` 的 `setUserRole` 方法之后（第 115 行后）添加：

```java
@Transactional
public User updateUsername(Long targetUserId, String newUsername, Long operatorUserId) {
    // 1. 验证权限
    User operator = userRepository.findById(operatorUserId)
            .orElseThrow(() -> new RuntimeException("操作者不存在"));

    boolean isSelf = targetUserId.equals(operatorUserId);
    boolean isSuperAdmin = operator.getRole() == UserRole.SUPER_ADMIN;

    if (!isSelf && !isSuperAdmin) {
        throw new SecurityException("无权限修改该用户名");
    }

    // 2. 验证用户名格式
    if (newUsername == null || newUsername.trim().isEmpty()) {
        throw new RuntimeException("用户名不能为空");
    }
    if (newUsername.length() < 1 || newUsername.length() > 30) {
        throw new RuntimeException("用户名长度必须在1-30个字符之间");
    }
    if (!newUsername.matches("^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$")) {
        throw new RuntimeException("用户名只能包含字母、数字、下划线和中文");
    }

    // 3. 检查用户名是否已被占用
    if (userRepository.existsByUsername(newUsername)) {
        throw new RuntimeException("用户名已被占用");
    }

    // 4. 更新用户名
    User targetUser = userRepository.findById(targetUserId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    targetUser.setUsername(newUsername);
    User saved = userRepository.save(targetUser);

    // 5. 清除缓存
    redisCacheService.evict("users::id:" + targetUserId);

    return saved;
}
```

- [ ] **Step 2: 验证编译通过**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/example/hello/service/UserService.java
git commit -m "feat: 在 UserService 中添加 updateUsername 方法"
```

---

## Task 3: 在 UserController 中添加 updateUsername 端点

**Files:**
- Modify: `src/main/java/com/example/hello/controller/UserController.java:135`

- [ ] **Step 1: 添加 import 语句**

在 `UserController.java` 的 import 区域添加：

```java
import com.example.hello.dto.UpdateUsernameRequest;
import jakarta.validation.Valid;
```

- [ ] **Step 2: 添加 updateUsername 端点**

在 `UserController.java` 的 `getUserIdFromToken` 方法之前（第 130 行前）添加：

```java
/**
 * 修改用户名
 * 用户可以修改自己的用户名，超级管理员可以修改任意用户的用户名
 */
@PutMapping("/{userId:\\d+}/username")
public ResponseEntity<?> updateUsername(
        @PathVariable Long userId,
        @RequestBody @Valid UpdateUsernameRequest request,
        @RequestHeader(value = "Authorization", required = false) String token) {
    try {
        if (token == null || token.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "未提供认证令牌"));
        }
        Long operatorUserId = getUserIdFromToken(token);
        User updatedUser = userService.updateUsername(userId, request.getUsername(), operatorUserId);
        return ResponseEntity.ok(updatedUser);
    } catch (SecurityException e) {
        return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
```

- [ ] **Step 3: 验证编译通过**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/example/hello/controller/UserController.java
git commit -m "feat: 在 UserController 中添加 updateUsername 端点"
```

---

## Task 4: 编写单元测试

**Files:**
- Create: `src/test/java/com/example/hello/service/UserServiceUpdateUsernameTest.java`

- [ ] **Step 1: 创建测试类**

```java
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
        // Arrange
        String newUsername = "newuser123";
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername(newUsername)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        User result = userService.updateUsername(1L, newUsername, 1L);

        // Assert
        assertNotNull(result);
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

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
}
```

- [ ] **Step 2: 运行测试**

Run: `mvn test -Dtest=UserServiceUpdateUsernameTest`
Expected: 所有测试通过

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/example/hello/service/UserServiceUpdateUsernameTest.java
git commit -m "test: 添加 UserService updateUsername 方法的单元测试"
```

---

## Task 5: 运行完整测试套件

**Files:**
- 无新增/修改文件

- [ ] **Step 1: 运行所有测试**

Run: `mvn test`
Expected: 所有测试通过，包括新增的测试

- [ ] **Step 2: 检查测试覆盖率（可选）**

Run: `mvn jacoco:report`
Expected: 查看新增代码的测试覆盖率

- [ ] **Step 3: 最终提交**

```bash
git add .
git commit -m "feat: 完成修改用户名功能的实现"
```

---

## 自我审查

### 1. Spec 覆盖检查

✅ **API 设计** - Task 3 实现了 `PUT /api/users/{userId}/username` 端点

✅ **数据模型** - Task 1 创建了 `UpdateUsernameRequest` DTO

✅ **业务逻辑** - Task 2 实现了完整的 `updateUsername` 方法

✅ **权限控制** - Task 2 包含了用户自己修改和管理员修改的权限验证

✅ **验证规则** - Task 1 和 Task 2 都包含了用户名格式验证

✅ **错误处理** - Task 3 实现了完整的错误响应（401/403/400）

✅ **缓存处理** - Task 2 包含了缓存清除逻辑

✅ **测试策略** - Task 4 和 Task 5 包含了单元测试和集成测试

### 2. 占位符扫描

✅ 无 "TBD"、"TODO" 或模糊描述

✅ 所有步骤都包含完整的代码

✅ 所有命令都有明确的预期输出

### 3. 类型一致性检查

✅ `UpdateUsernameRequest` 在 Task 1 定义，在 Task 3 使用

✅ `updateUsername` 方法签名在 Task 2 定义，在 Task 3 和 Task 4 使用

✅ `SecurityException` 在 Task 2 抛出，在 Task 3 捕获

✅ 所有方法名、参数名、变量名保持一致

---

*计划生成时间：2026-05-05*
*计划工具：Claude Code AI 计划助手*
