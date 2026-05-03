[根目录](../../../../CLAUDE.md) > [src](../../) > [main](../) > [java](../) > [com.example.hello](../) > **service**

# Service 模块

## 模块职责

业务逻辑层，实现核心业务规则，包括审核流程、编码生成、图片处理、区块链存证、AI 识别等。

## 服务清单

### 核心业务服务

| 服务 | 职责 | 关键依赖 |
|------|------|---------|
| `AuthService` | 用户注册（含邀请码验证）、登录、忘记密码、游客登录 | UserRepository, JwtUtil, InvitationCodeService |
| `AuditService` | 纹样审核（单个/批量）、重新提交、编码生成 | PatternHashService, BlockchainAnchorService, PatternCodeService |
| `DraftService` | 草稿 CRUD、提交到审核 | AuditService, ImageService |
| `PatternService` | 纹样 CRUD、二维码生成、水印嵌入/提取、下载 | PatternCodeService, DwtSvdWatermarkService, ImageService |
| `UserService` | 用户删除、信息查询、角色设置 | UserRepository, PatternDraftRepository |
| `StatsService` | 统计信息（今日提交、待审核、已通过、总数） | PatternPendingRepository, PatternRepository |

### 编码与哈希服务

| 服务 | 职责 |
|------|------|
| `PatternCodeService` | 纹样编码生成、验证、回收、标签解析 |
| `PatternHashService` | 图片 SHA-256 哈希计算 |

### 存储与存证服务

| 服务 | 职责 |
|------|------|
| `ImageService` | S3 对象存储操作（上传、移动、复制、删除、下载） |
| `BlockchainAnchorService` | 区块链存证（至信链/EVM 两种模式） |

### 水印服务

| 服务 | 职责 |
|------|------|
| `DwtSvdWatermarkService` | DWT-SVD 鲁棒水印嵌入与提取（纯 Java 实现） |

### AI 识别服务

| 服务 | 职责 |
|------|------|
| `AiPatternRecognitionService` | 单张图片 AI 纹样识别（调用多模态大模型） |
| `AiBatchEntryService` | AI 批量识别预览/提交（支持同步/异步） |

### 邀请码服务

| 服务 | 职责 |
|------|------|
| `InvitationCodeService` | 本地邀请码生成、消费；剪艺码外部验证 |
| `AppInvitationCodeVerifier` | 外部邀请码验证接口（SPI） |
| `AppRegistrationCallbackService` | 注册成功后回调外部系统 |

## 关键流程

### 审核通过流程（AuditService.moveToPattern）
1. 确保编码存在
2. 根据图片来源类型处理图片（移动/复制/抓取）
3. 计算图片 SHA-256 哈希
4. 保存到正式表
5. 触发区块链存证（失败不影响入库）

### AI 批量识别流程
1. `AiBatchEntryService.startPreviewTask()` - 异步启动预览任务
2. 每张图片调用 `AiPatternRecognitionService.recognizeByImageUrl()`
3. 解析 AI 返回结果，匹配编码体系
4. 前端展示预览结果，用户可修正
5. `AiBatchEntryService.confirm()` - 确认提交到审核

## 相关文件

- `AuthService.java` - 认证服务
- `AuditService.java` - 审核服务
- `DraftService.java` - 草稿服务
- `PatternService.java` - 纹样服务
- `ImageService.java` - 对象存储服务
- `StatsService.java` - 统计服务
- `UserService.java` - 用户服务
- `PatternCodeService.java` - 编码生成服务
- `PatternHashService.java` - 图片哈希服务
- `BlockchainAnchorService.java` - 区块链存证服务
- `DwtSvdWatermarkService.java` - DWT-SVD 水印服务
- `AiPatternRecognitionService.java` - AI 纹样识别服务
- `AiBatchEntryService.java` - AI 批量录入服务
- `InvitationCodeService.java` - 邀请码服务
- `AppInvitationCodeVerifier.java` - 外部邀请码验证接口
- `AppRegistrationCallbackService.java` - 注册回调服务
