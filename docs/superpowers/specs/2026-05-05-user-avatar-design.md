# 用户头像功能设计文档

> 创建时间：2026-05-05
> 版本：1.0
> 状态：已批准

---

## 一、需求概述

为系统添加用户头像功能，允许用户上传和修改个人头像，提升用户体验和个性化程度。

### 核心目标

1. 支持用户上传和修改个人头像
2. 复用现有S3对象存储架构
3. 实现细粒度的权限控制
4. 自动清理旧头像，节省存储空间

---

## 二、功能需求

### 2.1 核心功能

- **头像上传**：用户可以上传图片作为个人头像
- **头像修改**：用户可以随时更换头像
- **头像获取**：获取用户头像URL用于展示
- **自动清理**：上传新头像时自动删除旧头像

### 2.2 权限控制

| 角色 | 修改自己头像 | 修改他人头像 |
|------|------------|------------|
| 普通用户 | ✅ | ❌ |
| 管理员 | ✅ | ❌ |
| 超级管理员 | ✅ | ✅ |

---

## 三、技术规格

### 3.1 存储规格

| 项目 | 规格 |
|------|------|
| 存储方式 | S3对象存储（复用现有架构） |
| 文件大小 | 最大2MB |
| 图片格式 | JPEG、PNG、WebP、GIF、BMP |
| 尺寸限制 | 不限制（前端负责裁剪压缩） |
| 存储路径 | `avatars/{userId}/avatar.{ext}` |

### 3.2 性能要求

- 上传响应时间：< 3秒（含S3上传）
- 获取头像URL响应时间：< 100ms

---

## 四、数据模型变更

### 4.1 User实体修改

**文件位置**：`src/main/java/com/example/hello/entity/User.java`

**新增字段**：

```java
@Column(name = "avatar_url")
private String avatarUrl;
```

**Getter/Setter**：

```java
public String getAvatarUrl() { return avatarUrl; }
public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
```

### 4.2 数据库变更

**SQL脚本**：

```sql
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(500) DEFAULT NULL COMMENT '用户头像URL';
```

**字段说明**：

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| avatar_url | VARCHAR(500) | NULL | 头像在S3中的完整URL |

---

## 五、API接口设计

### 5.1 上传/修改头像

**端点**：`POST /api/users/{userId}/avatar`

**请求**：

```
Content-Type: multipart/form-data
Authorization: Bearer <token>

参数：
- userId: 用户ID（路径参数）
- file: 图片文件（最大2MB）
```

**成功响应**（200）：

```json
{
  "message": "头像上传成功",
  "avatarUrl": "https://s3.example.com/avatars/1/avatar.jpg"
}
```

**错误响应**：

| 状态码 | 场景 | 响应体 |
|--------|------|--------|
| 400 | 文件为空 | `{"message": "上传文件不能为空"}` |
| 400 | 文件过大 | `{"message": "文件大小超过限制（最大2MB）"}` |
| 400 | 格式不支持 | `{"message": "仅支持图片文件（JPEG、PNG、WebP等）"}` |
| 403 | 权限不足 | `{"message": "无权修改该用户的头像"}` |
| 404 | 用户不存在 | `{"message": "用户不存在"}` |
| 500 | S3上传失败 | `{"message": "头像上传失败"}` |

### 5.2 获取头像URL

**端点**：`GET /api/users/{userId}/avatar`

**请求**：

```
Authorization: Bearer <token>

参数：
- userId: 用户ID（路径参数）
```

**成功响应**（200）：

```json
{
  "avatarUrl": "https://s3.example.com/avatars/1/avatar.jpg"
}
```

**错误响应**：

| 状态码 | 场景 | 响应体 |
|--------|------|--------|
| 404 | 用户不存在 | `{"message": "用户不存在"}` |
| 404 | 无头像 | `{"message": "用户暂未设置头像"}` |

---

## 六、实现方案

### 6.1 架构设计

采用扩展现有服务的方案，复用ImageService的S3操作能力：

```
┌─────────────────────────────────────────────────────────────┐
│                      UserController                         │
│  POST /api/users/{userId}/avatar                           │
│  GET /api/users/{userId}/avatar                            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      UserService                            │
│  updateAvatar(userId, file, operatorUserId)                │
│  getAvatarUrl(userId)                                      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      ImageService                           │
│  uploadAvatar(file, userId)                                │
│  deleteAvatar(userId)                                      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      S3 Object Storage                      │
│  avatars/{userId}/avatar.{ext}                             │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 ImageService扩展

**新增常量**：

```java
private static final String AVATAR_FOLDER = "avatars/";
private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024; // 2MB
```

**新增方法**：

#### uploadAvatar(MultipartFile file, Long userId)

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

#### deleteAvatar(Long userId)

```java
/**
 * 删除用户头像
 * @param userId 用户ID
 */
public void deleteAvatar(Long userId) {
    try {
        // 列出并删除该用户的所有头像文件
        String prefix = AVATAR_FOLDER + userId + "/";
        // 实际实现需要先列出对象再删除
        // 简化版本：直接尝试删除常见扩展名
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

### 6.3 UserService扩展

**新增依赖**：

```java
private final ImageService imageService;
```

**新增方法**：

#### updateAvatar(Long userId, MultipartFile file, Long operatorUserId)

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

#### getAvatarUrl(Long userId)

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

### 6.4 UserController扩展

**新增依赖**：

```java
private final ImageService imageService;
```

**新增端点**：

#### uploadAvatar

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

#### getAvatarUrl

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

---

## 七、错误处理

### 7.1 异常类型

| 异常类型 | 场景 | HTTP状态码 |
|---------|------|-----------|
| IllegalArgumentException | 参数验证失败（文件为空、格式不支持、大小超限） | 400 |
| SecurityException | 权限不足 | 403 |
| RuntimeException | 业务逻辑错误（用户不存在） | 400 |
| IOException | S3操作失败 | 500 |

### 7.2 全局异常处理

由现有的`GlobalExceptionHandler`统一处理，无需额外配置。

---

## 八、测试策略

### 8.1 单元测试

**ImageService测试**：

- 测试uploadAvatar方法
  - 正常上传
  - 文件为空
  - 文件过大
  - 格式不支持
  - S3上传失败

- 测试deleteAvatar方法
  - 正常删除
  - 文件不存在（应忽略）

**UserService测试**：

- 测试updateAvatar方法
  - 正常更新
  - 用户不存在
  - 权限不足（非本人且非超级管理员）
  - 删除旧头像失败
  - 上传新头像失败

- 测试getAvatarUrl方法
  - 正常获取
  - 用户不存在
  - 无头像

### 8.2 集成测试

**UserController测试**：

- 测试POST /api/users/{userId}/avatar
  - 正常上传
  - 各种错误场景

- 测试GET /api/users/{userId}/avatar
  - 正常获取
  - 无头像

---

## 九、实现步骤

### 阶段1：数据层变更

1. 修改User实体，添加avatarUrl字段
2. 执行数据库迁移脚本

### 阶段2：服务层实现

1. 扩展ImageService，添加uploadAvatar和deleteAvatar方法
2. 扩展UserService，添加updateAvatar和getAvatarUrl方法

### 阶段3：控制器层实现

1. 扩展UserController，添加头像上传和获取端点

### 阶段4：测试验证

1. 编写单元测试
2. 执行集成测试
3. 功能验证

---

## 十、相关文件

### 需要修改的文件

- `src/main/java/com/example/hello/entity/User.java` - 添加avatarUrl字段
- `src/main/java/com/example/hello/service/ImageService.java` - 添加头像上传/删除方法
- `src/main/java/com/example/hello/service/UserService.java` - 添加头像更新/获取方法
- `src/main/java/com/example/hello/controller/UserController.java` - 添加头像API端点

### 需要创建的文件

- 数据库迁移脚本（可选，或直接执行SQL）

### 测试文件

- `src/test/java/com/example/hello/service/ImageServiceTest.java` - ImageService测试
- `src/test/java/com/example/hello/service/UserServiceTest.java` - UserService测试
- `src/test/java/com/example/hello/controller/UserControllerTest.java` - UserController测试

---

## 十一、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| S3服务不可用 | 头像上传失败 | 返回友好错误提示，允许稍后重试 |
| 文件大小超限 | 上传失败 | 前端预检查 + 后端验证双重保障 |
| 权限绕过 | 安全风险 | 严格的权限验证逻辑，不依赖前端 |
| 旧头像清理失败 | 存储空间浪费 | 记录日志，定期清理任务 |

---

## 十二、后续扩展

### 可选增强功能

1. **默认头像**：为未设置头像的用户生成默认头像（基于用户名首字母）
2. **头像缓存**：添加CDN缓存，提升访问速度
3. **头像裁剪**：后端支持图片裁剪参数
4. **历史头像**：保留头像历史版本，支持回滚

---

*文档生成时间：2026-05-05*
*设计者：蕾姆*
