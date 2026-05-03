[根目录](../../../../CLAUDE.md) > [src](../../) > [main](../) > [java](../) > [com.example.hello](../) > **controller**

# Controller 模块

## 模块职责

REST API 控制器层，负责接收 HTTP 请求、参数校验、调用 Service 层、返回响应。

## 控制器清单

| 控制器 | 路径前缀 | 职责 |
|--------|---------|------|
| `AuthController` | `/api/auth` | 用户注册、登录、忘记密码、游客登录 |
| `AuditController` | `/api/audit` | 纹样审核、AI 批量识别预览/提交 |
| `DraftController` | `/api/drafts` | 草稿 CRUD、提交到审核 |
| `PatternController` | `/api/patterns` | 纹样 CRUD、二维码、水印验证、下载 |
| `ImageController` | `/api/images` | 图片/故事文件上传、删除 |
| `StatsController` | `/api/stats` | 统计信息查询 |
| `UserController` | `/api/users` | 用户管理、角色设置、邀请码生成 |

## 关键设计

- 所有需要认证的接口通过 `@RequestHeader("Authorization") String token` 接收 JWT
- 使用 `JwtUtil.extractUserId(token)` 提取用户 ID
- 使用 `@PageableDefault(size = 20)` 支持分页
- 使用 `@Valid` + DTO 进行参数校验
- 异常统一由 `GlobalExceptionHandler` 处理

## 相关文件

- `AuthController.java` - 认证接口
- `AuditController.java` - 审核接口（含 AI 批量）
- `DraftController.java` - 草稿接口
- `PatternController.java` - 纹样接口（含二维码、水印、下载）
- `ImageController.java` - 图片上传接口
- `StatsController.java` - 统计接口
- `UserController.java` - 用户+邀请码接口
