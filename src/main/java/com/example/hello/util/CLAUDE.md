[根目录](../../../../CLAUDE.md) > [src](../../) > [main](../) > [java](../) > [com.example.hello](../) > **util**

# Util 模块

## 模块职责

工具类，提供通用功能。

## 工具清单

| 工具类 | 职责 |
|--------|------|
| `JwtUtil` | JWT Token 生成、解析、验证 |

## JwtUtil 详细说明

### 功能
- `generateToken(userId, username, role)` - 生成 JWT Token
- `extractUsername(token)` - 从 Token 提取用户名
- `extractUserId(token)` - 从 Token 提取用户 ID
- `extractRole(token)` - 从 Token 提取用户角色
- `validateToken(token, username)` - 验证 Token 有效性

### 配置
- 密钥：`jwt.secret`（256 位）
- 过期时间：`jwt.expiration`（毫秒，默认 24 小时）

### Token 结构
```json
{
  "sub": "username",
  "userId": 1,
  "role": "SUPER_ADMIN",
  "iat": 1234567890,
  "exp": 1234654290
}
```

## 相关文件

- `JwtUtil.java` - JWT 工具类
