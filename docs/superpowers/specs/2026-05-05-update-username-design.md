# 修改用户名功能设计文档

> 创建时间：2026-05-05
> 状态：设计完成，待实现

---

## 1. 概述

### 1.1 功能目标

为系统添加修改用户名的功能，支持：
- 用户修改自己的用户名
- 超级管理员修改任意用户的用户名

### 1.2 需求来源

用户提出需要新增修改用户名的功能，经过需求澄清后确定以下规格：
- **操作权限**：用户可以改自己的，管理员也可以改别人的
- **用户名规则**：1-30个字符，允许字母、数字、下划线、中文
- **频率限制**：无限制
- **安全验证**：仅需登录状态（JWT有效即可）

---

## 2. API 设计

### 2.1 端点

```
PUT /api/users/{userId}/username
```

### 2.2 请求头

```
Authorization: Bearer <jwt-token>
Content-Type: application/json
```

### 2.3 请求体

```json
{
  "username": "newUsername123"
}
```

### 2.4 响应

#### 成功响应（200）

```json
{
  "id": 1,
  "username": "newUsername123",
  "role": "USER",
  "createdAt": "2024-01-01T00:00:00"
}
```

#### 错误响应

| 场景 | HTTP 状态码 | 错误消息 |
|------|------------|---------|
| 未提供 JWT | 401 | 未提供认证令牌 |
| JWT 无效/过期 | 401 | 令牌无效 |
| 用户不存在 | 400 | 用户不存在 |
| 无权限修改 | 403 | 无权限修改该用户名 |
| 用户名为空 | 400 | 用户名不能为空 |
| 用户名过长/过短 | 400 | 用户名长度必须在1-30个字符之间 |
| 用户名格式错误 | 400 | 用户名只能包含字母、数字、下划线和中文 |
| 用户名已被占用 | 400 | 用户名已被占用 |

### 2.5 权限规则

- 用户可以修改自己的用户名（`{userId}` 等于当前登录用户 ID）
- 超级管理员可以修改任意用户的用户名
- 其他情况返回 403

---

## 3. 数据模型

### 3.1 请求 DTO

**文件**：`src/main/java/com/example/hello/dto/UpdateUsernameRequest.java`

```java
package com.example.hello.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateUsernameRequest {
    @Size(min = 1, max = 30, message = "用户名长度必须在1-30个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$", 
             message = "用户名只能包含字母、数字、下划线和中文")
    private String username;

    // getter 和 setter
}
```

### 3.2 验证规则

| 规则 | 说明 |
|------|------|
| 长度 | 1-30 个字符 |
| 允许字符 | 字母、数字、下划线、中文 |
| 唯一性 | 数据库中不能存在相同用户名 |

---

## 4. 业务逻辑

### 4.1 UserService 新增方法

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

### 4.2 处理流程

```
验证权限 (自己或超级管理员)
    ↓
验证用户名格式 (非空、长度、字符)
    ↓
检查唯一性 (数据库查询)
    ↓
更新数据库
    ↓
清除缓存
    ↓
返回更新后的用户信息
```

---

## 5. 控制器实现

### 5.1 UserController 新增方法

```java
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

---

## 6. 数据库变更

### 6.1 无需变更

`users` 表已存在 `username` 字段，且已设置唯一约束：

```java
@Column(unique = true, nullable = false)
private String username;
```

无需修改数据库结构。

---

## 7. 缓存处理

### 7.1 缓存清除

更新用户名后，需要清除用户缓存：

```java
redisCacheService.evict("users::id:" + targetUserId);
```

### 7.2 缓存键

- 用户信息缓存键：`users::id:{userId}`
- TTL：5 分钟（参考现有 UserService）

---

## 8. 测试策略

### 8.1 单元测试（UserServiceTest）

```java
@Test
void updateUsername_Success() {
    // 测试正常修改用户名
}

@Test
void updateUsername_SelfModification() {
    // 测试用户修改自己的用户名
}

@Test
void updateUsername_AdminModification() {
    // 测试管理员修改其他用户的用户名
}

@Test
void updateUsername_NoPermission() {
    // 测试普通用户修改他人用户名（应抛出异常）
}

@Test
void updateUsername_EmptyUsername() {
    // 测试空用户名（应抛出异常）
}

@Test
void updateUsername_InvalidLength() {
    // 测试长度不合法（应抛出异常）
}

@Test
void updateUsername_InvalidCharacters() {
    // 测试非法字符（应抛出异常）
}

@Test
void updateUsername_DuplicateUsername() {
    // 测试用户名已被占用（应抛出异常）
}
```

### 8.2 集成测试（UserControllerTest）

```java
@Test
void updateUsername_API_Success() {
    // 测试 API 端点正常工作
}

@Test
void updateUsername_API_Unauthorized() {
    // 测试未认证请求
}

@Test
void updateUsername_API_Forbidden() {
    // 测试无权限请求
}
```

---

## 9. 实现清单

### 9.1 文件变更

| 操作 | 文件路径 | 说明 |
|------|---------|------|
| 新增 | `src/main/java/com/example/hello/dto/UpdateUsernameRequest.java` | 请求 DTO |
| 修改 | `src/main/java/com/example/hello/service/UserService.java` | 添加 `updateUsername` 方法 |
| 修改 | `src/main/java/com/example/hello/controller/UserController.java` | 添加 `updateUsername` 端点 |
| 新增 | `src/test/java/com/example/hello/service/UserServiceUpdateUsernameTest.java` | 单元测试 |

### 9.2 实现顺序

1. 创建 `UpdateUsernameRequest` DTO
2. 在 `UserService` 中添加 `updateUsername` 方法
3. 在 `UserController` 中添加 `updateUsername` 端点
4. 编写单元测试
5. 运行测试验证

---

## 10. 注意事项

### 10.1 事务管理

- 使用 `@Transactional` 注解确保数据一致性
- 更新操作在事务中执行，失败时自动回滚

### 10.2 并发处理

- 数据库唯一约束防止并发创建相同用户名
- 缓存清除确保后续查询获取最新数据

### 10.3 安全性

- 仅需 JWT 认证，无需密码验证
- 权限检查确保用户只能修改自己的用户名（除非是超级管理员）

### 10.4 扩展性

- 未来可扩展用户头像、昵称等功能
- 可考虑添加修改历史记录（如需要）

---

## 11. 相关文件

- `src/main/java/com/example/hello/entity/User.java` - 用户实体
- `src/main/java/com/example/hello/controller/UserController.java` - 用户控制器
- `src/main/java/com/example/hello/service/UserService.java` - 用户服务
- `src/main/java/com/example/hello/repository/UserRepository.java` - 用户仓库
- `src/main/java/com/example/hello/util/JwtUtil.java` - JWT 工具
- `src/main/java/com/example/hello/service/RedisCacheService.java` - 缓存服务

---

*文档生成时间：2026-05-05*
*设计工具：Claude Code AI 设计助手*
