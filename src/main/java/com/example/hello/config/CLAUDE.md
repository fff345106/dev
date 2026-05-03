[根目录](../../../../CLAUDE.md) > [src](../../) > [main](../) > [java](../) > [com.example.hello](../) > **config**

# Config 模块

## 模块职责

Spring 配置类，定义安全策略、数据初始化、定时任务和外部服务配置属性。

## 配置清单

| 配置类 | 职责 |
|--------|------|
| `SecurityConfig` | Spring Security 配置（CORS、密码编码器、安全过滤链） |
| `DataInitializer` | 应用启动时初始化默认超级管理员账号 |
| `ScheduledTasks` | 定时任务（每天凌晨 2 点清理已审核记录） |
| `BlockchainProperties` | 区块链存证配置属性（至信链/EVM） |
| `AiProperties` | AI 识别配置属性（API 密钥、模型、并发数） |
| `AppInvitationCodeProperties` | 邀请码/剪艺码校验配置属性 |

## 关键设计

### SecurityConfig
- 当前配置放行了所有请求（`anyRequest().permitAll()`），生产环境需收紧
- 启用 CORS，允许所有来源
- 禁用 CSRF，使用无状态 Session

### BlockchainProperties
- 支持两种 Provider：`ZXCHAIN_OPEN`（至信链）和 `EVM`（兼容链）
- 至信链模式需要本地 GO SDK 签名服务

### AiProperties
- 支持两种 AI API：Chat Completions API 和 Conversation Chat API
- 批量并发数和队列容量可配置

## 相关文件

- `SecurityConfig.java` - 安全配置
- `DataInitializer.java` - 数据初始化
- `ScheduledTasks.java` - 定时任务
- `BlockchainProperties.java` - 区块链配置属性
- `AiProperties.java` - AI 配置属性
- `AppInvitationCodeProperties.java` - 邀请码配置属性
