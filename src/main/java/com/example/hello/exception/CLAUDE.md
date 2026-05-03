[根目录](../../../../CLAUDE.md) > [src](../../) > [main](../) > [java](../) > [com.example.hello](../) > **exception**

# Exception 模块

## 模块职责

全局异常处理，统一 API 错误响应格式。

## 处理器清单

| 处理器 | 职责 |
|--------|------|
| `GlobalExceptionHandler` | 全局异常捕获与处理 |

## 异常处理规则

| 异常类型 | HTTP 状态码 | 响应格式 |
|---------|-----------|---------|
| `RuntimeException` | 400 Bad Request | `AuthResponse(null, message)` |
| `MethodArgumentNotValidException` | 400 Bad Request | `AuthResponse(null, fieldError.defaultMessage)` |

## 设计说明

- 使用 `@RestControllerAdvice` 注解
- 返回统一的 `AuthResponse` 格式（即使不是认证相关）
- 参数校验异常提取第一个字段错误信息

## 相关文件

- `GlobalExceptionHandler.java` - 全局异常处理器
