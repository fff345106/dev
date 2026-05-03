[根目录](../../../../CLAUDE.md) > [src](../../) > [main](../) > [java](../) > [com.example.hello](../) > **repository**

# Repository 模块

## 模块职责

Spring Data JPA 仓库接口，提供数据访问层抽象。

## 仓库清单

| 仓库 | 实体 | 关键查询 |
|------|------|---------|
| `UserRepository` | User | findByUsername, existsByUsername |
| `PatternRepository` | Pattern | findByPatternCode, findByMainCategory/Style/Region/Period, findMaxSequenceNumberByDateCode |
| `PatternDraftRepository` | PatternDraft | findByUserIdOrderByUpdatedAtDesc, countByUserId |
| `PatternPendingRepository` | PatternPending | findByStatus, findBySubmitterId, findRecyclableCodes, findMaxActiveSequenceNumberByDateCode, findApprovedBeforeTime |
| `InvitationCodeRepository` | InvitationCode | findByCode, findByCodeForUpdate（悲观锁） |

## 关键设计

- `PatternRepository` 继承 `JpaSpecificationExecutor` 支持动态查询
- `PatternPendingRepository.findRecyclableCodes()` - 查询可回收的被驳回编码
- `PatternPendingRepository.findMaxActiveSequenceNumberByDateCode()` - 查询当天最大活跃序列号
- `InvitationCodeRepository.findByCodeForUpdate()` - 使用悲观锁防止并发消费

## 相关文件

- `UserRepository.java` - 用户仓库
- `PatternRepository.java` - 纹样仓库
- `PatternDraftRepository.java` - 草稿仓库
- `PatternPendingRepository.java` - 待审核仓库
- `InvitationCodeRepository.java` - 邀请码仓库
