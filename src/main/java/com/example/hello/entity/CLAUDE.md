[根目录](../../../../CLAUDE.md) > [src](../../) > [main](../) > [java](../) > [com.example.hello](../) > **entity**

# Entity 模块

## 模块职责

JPA 实体定义，对应数据库表结构。

## 实体清单

| 实体 | 表名 | 职责 |
|------|------|------|
| `User` | `users` | 用户信息（用户名、密码、角色） |
| `Pattern` | `patterns` | 正式纹样（含编码、图片、区块链存证信息） |
| `PatternDraft` | `pattern_drafts` | 草稿纹样（关联用户） |
| `PatternPending` | `patterns_pending` | 待审核纹样（含审核状态、提交人、审核人） |
| `InvitationCode` | `invitation_codes` | 邀请码（6位数字，一次性使用） |

## 关键设计

### Pattern 实体扩展字段
- `imageSourceType` - 图片来源类型（TEMP_UPLOAD/EXTERNAL/LIBRARY）
- `storyText` / `storyImageUrl` - 藏品故事
- `imageHash` / `hashAlgorithm` - 图片哈希（SHA-256）
- `chainTxHash` / `chainBlockNumber` / `chainTimestamp` / `chainStatus` - 区块链存证信息
- `status` - 记录状态（默认 APPROVED）

### 关联关系
- `PatternDraft` → `User`（ManyToOne，LAZY）
- `PatternPending` → `User` submitter（ManyToOne，LAZY）
- `PatternPending` → `User` auditor（ManyToOne，LAZY）

### 序列化注意
- `User.password` 使用 `@JsonIgnore` 隐藏
- LAZY 加载的关联字段使用 `@JsonIgnoreProperties` 防止序列化异常

## 相关文件

- `User.java` - 用户实体
- `Pattern.java` - 正式纹样实体
- `PatternDraft.java` - 草稿实体
- `PatternPending.java` - 待审核实体
- `InvitationCode.java` - 邀请码实体
