[根目录](../../../../CLAUDE.md) > [src](../../) > [main](../) > [java](../) > [com.example.hello](../) > **dto**

# DTO 模块

## 模块职责

数据传输对象，定义 API 请求和响应的数据结构。

## DTO 清单

### 认证相关
- `LoginRequest` - 登录请求（用户名、密码）
- `RegisterRequest` - 注册请求（用户名、密码、确认密码、邀请码）
- `ForgotPasswordRequest` - 忘记密码请求
- `AuthResponse` - 认证响应（token、message）

### 纹样相关
- `PatternRequest` - 纹样请求（描述、分类、图片、故事）
- `DraftRequest` - 草稿请求
- `BatchDownloadRequest` - 批量下载请求（ID 列表）

### 审核相关
- `AuditRequest` - 审核请求（approved、rejectReason）
- `BatchAuditRequest` - 批量审核请求（ID 列表、approved、rejectReason）

### AI 批量识别相关
- `AiBatchPreviewRequest` - AI 批量预览请求（图片 URL 列表、风格/地区/时期覆盖）
- `AiBatchPreviewResponse` - AI 批量预览响应
- `AiBatchPreviewItem` - 单个预览项
- `AiBatchConfirmRequest` - AI 批量确认请求
- `AiBatchConfirmItem` - 单个确认项
- `AiBatchSubmitRequest` - AI 批量提交请求
- `AiBatchSubmitResponse` - AI 批量提交响应
- `AiBatchSubmitItemResult` - 单个提交结果
- `AiBatchTaskStartResponse` - 异步任务启动响应
- `AiBatchTaskProgressResponse` - 异步任务进度响应
- `AiBatchPreviewTaskProgressResponse` - 预览任务进度响应

### 统计相关
- `StatsResponse` - 统计响应（今日提交、待审核、已通过、总数）

## 相关文件

所有 DTO 文件位于此目录下。
