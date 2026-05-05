# 剪艺 App 用户评价接口设计

> 日期：2026-05-05
> 状态：设计完成，待审阅

## 概述

为剪艺 App 新增用户评价功能，允许登录用户对 App 整体体验进行星级评分和文字评论。包含评价提交、列表查询、统计展示和管理员管理等完整功能。

## 需求总结

| 项目 | 说明 |
|------|------|
| 评价对象 | 剪艺 App 整体体验 |
| 评价形式 | 1-5 星评分 + 文字评论（评论可选） |
| 评价频率 | 登录用户可随时提交，不限次数 |
| 权限控制 | 仅登录用户（USER/ADMIN/SUPER_ADMIN）可评价，游客只能查看 |
| 展示功能 | 评价列表分页、平均评分统计、管理员删除、查看我的评价 |

## 数据模型

### AppReview 实体（`app_reviews` 表）

| 字段 | 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|------|
| `id` | `id` | `Long` | 主键，自增 | 主键 |
| `user` | `user_id` | `User` | `@ManyToOne(LAZY)` | 评价用户 |
| `rating` | `rating` | `Integer` | `nullable = false` | 星级评分（1-5） |
| `comment` | `comment` | `String` | `TEXT`，可为空 | 文字评论 |
| `createdAt` | `created_at` | `LocalDateTime` | 自动填充 | 创建时间 |
| `updatedAt` | `updated_at` | `LocalDateTime` | 自动填充 | 更新时间 |

**设计要点：**
- `rating` 使用 `Integer` 而非枚举，便于后续扩展
- `comment` 允许为空（用户可能只想给星级不写评论）
- `updatedAt` 支持用户修改评价时更新时间戳
- 与 User 实体通过 `@ManyToOne` 关联，查询时可获取评价者信息

## API 接口设计

### 评价管理 `/api/reviews`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| `POST` | `/api/reviews` | 提交评价 | 登录用户 |
| `GET` | `/api/reviews` | 评价列表（分页） | 所有人（含游客） |
| `GET` | `/api/reviews/stats` | 平均评分统计 | 所有人（含游客） |
| `GET` | `/api/reviews/my` | 我的评价列表 | 登录用户 |
| `DELETE` | `/api/reviews/{id}` | 删除评价 | 管理员/超级管理员 |

### 接口详情

#### 1. 提交评价 `POST /api/reviews`

**请求头：** `Authorization: Bearer <token>`（必填）

**请求体：**
```json
{
  "rating": 5,
  "comment": "非常好用的App！"
}
```

**校验规则：**
- `rating`：必填，整数，范围 1-5
- `comment`：可选，最长 500 字符

**响应：** `201 Created`
```json
{
  "id": 1,
  "user": { "id": 1, "username": "zhangsan", "role": "USER" },
  "rating": 5,
  "comment": "非常好用的App！",
  "createdAt": "2026-05-05T10:30:00",
  "updatedAt": "2026-05-05T10:30:00"
}
```

#### 2. 评价列表 `GET /api/reviews?page=0&size=20`

**请求头：** 无需认证

**响应：** `200 OK`
```json
{
  "content": [
    {
      "id": 1,
      "user": { "id": 1, "username": "zhangsan", "role": "USER" },
      "rating": 5,
      "comment": "非常好用的App！",
      "createdAt": "2026-05-05T10:30:00",
      "updatedAt": "2026-05-05T10:30:00"
    }
  ],
  "totalElements": 128,
  "totalPages": 7,
  "size": 20,
  "number": 0
}
```

按创建时间倒序排列。

#### 3. 平均评分 `GET /api/reviews/stats`

**请求头：** 无需认证

**响应：** `200 OK`
```json
{
  "averageRating": 4.5,
  "totalReviews": 128
}
```

Redis 缓存 5 分钟，缓存键：`reviews::stats`。

#### 4. 我的评价 `GET /api/reviews/my?page=0&size=20`

**请求头：** `Authorization: Bearer <token>`（必填）

**响应：** `200 OK`（格式同评价列表，仅返回当前用户的评价）

#### 5. 删除评价 `DELETE /api/reviews/{id}`

**请求头：** `Authorization: Bearer <token>`（必填，需 ADMIN 或 SUPER_ADMIN 角色）

**响应：** `200 OK`

物理删除，同时清除 stats 缓存。

## 业务逻辑

### Service 层设计

**AppReviewService** 负责：
- `submitReview(userId, rating, comment)` — 创建评价，关联用户
- `getReviews(pageable)` — 分页查询所有评价（关联加载用户信息）
- `getStats()` — 计算平均评分和总数（Redis 缓存 5 分钟）
- `getMyReviews(userId, pageable)` — 分页查询指定用户的评价
- `deleteReview(reviewId, operatorRole)` — 管理员删除评价

### 缓存策略

```
缓存键：reviews::stats
TTL：5 分钟
写入时机：首次查询 stats 时
失效时机：提交新评价或删除评价时主动清除
```

与现有 `RedisCacheService` 模式完全一致。

## 错误处理

| 场景 | 错误信息 | HTTP 状态 |
|------|---------|-----------|
| 评分超出范围 | `"评分必须在 1-5 之间"` | 400 |
| 评价不存在 | `"评价不存在"` | 400 |
| 无权删除 | `"仅管理员可以删除评价"` | 400 |
| 未登录 | `"请先登录"` | 400 |

错误通过 `GlobalExceptionHandler` 统一处理，返回 `AuthResponse(null, message)` 格式。

## 需要新增/修改的文件

### 新增文件

| 文件路径 | 说明 |
|---------|------|
| `entity/AppReview.java` | 评价实体 |
| `repository/AppReviewRepository.java` | 评价数据访问 |
| `dto/AppReviewRequest.java` | 提交评价请求 DTO |
| `dto/AppReviewStatsResponse.java` | 评分统计响应 DTO |
| `service/AppReviewService.java` | 评价业务逻辑 |
| `controller/AppReviewController.java` | 评价 REST API |

### 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `service/RedisCacheService.java` | 添加 reviews 相关缓存方法 |

## 技术约束

- 遵循项目现有的代码风格和架构模式
- 使用构造器注入（Controller 和 Service）
- 使用 Jakarta Bean Validation 校验请求参数
- 使用 `@Transactional` 管理写操作事务
- 手动提取 JWT Token 进行认证
- 错误通过抛出 `RuntimeException` 由 `GlobalExceptionHandler` 统一处理
