# 中国传统纹样数字档案系统 — 对外公开 API 接口文档

> **版本**: v1.0
> **最后更新**: 2026-05-05
> **Base URL**: `https://{your-domain}`
> **内容类型**: `application/json`（除特别说明外）
> **编码**: UTF-8
> **认证**: 以下所有接口均 **无需认证**，可直接调用

---

## 目录

- [一、公开 API（/api/open）](#一公开-apiapiopen)
  - [1.1 纹样图片列表](#11-纹样图片列表)
  - [1.2 纹样详情](#12-纹样详情)
  - [1.3 纹样详情页（HTML）](#13-纹样详情页html)
  - [1.4 公开藏品列表](#14-公开藏品列表)
  - [1.5 公开藏品详情](#15-公开藏品详情)
  - [1.6 公开活动列表](#16-公开活动列表)
- [二、用户认证（/api/auth）](#二用户认证apiauth)
  - [2.1 用户注册](#21-用户注册)
  - [2.2 用户登录](#22-用户登录)
  - [2.3 忘记密码](#23-忘记密码)
  - [2.4 游客登录](#24-游客登录)
- [三、纹样查询与操作（/api/patterns）](#三纹样查询与操作apipatterns)
  - [3.1 创建纹样](#31-创建纹样)
  - [3.2 获取所有纹样](#32-获取所有纹样)
  - [3.3 根据 ID 查询纹样](#33-根据-id-查询纹样)
  - [3.4 根据编码查询纹样](#34-根据编码查询纹样)
  - [3.5 获取纹样二维码（按 ID）](#35-获取纹样二维码按-id)
  - [3.6 获取纹样二维码（按编码）](#36-获取纹样二维码按编码)
  - [3.7 更新纹样](#37-更新纹样)
  - [3.8 按主类别查询](#38-按主类别查询)
  - [3.9 按风格查询](#39-按风格查询)
  - [3.10 按地区查询](#310-按地区查询)
  - [3.11 按时期查询](#311-按时期查询)
  - [3.12 下载纹样图片（含水印）](#312-下载纹样图片含水印)
  - [3.13 批量下载纹样图片（含水印）](#313-批量下载纹样图片含水印)
  - [3.14 嵌入水印](#314-嵌入水印)
  - [3.15 验证图片水印](#315-验证图片水印)
- [四、审核记录查询（/api/audit）](#四审核记录查询apiaudit)
  - [4.1 获取待审核列表](#41-获取待审核列表)
  - [4.2 获取所有审核记录](#42-获取所有审核记录)
  - [4.3 按状态查询审核记录](#43-按状态查询审核记录)
  - [4.4 根据 ID 查询审核记录](#44-根据-id-查询审核记录)
  - [4.5 AI 批量识别预览（同步）](#45-ai-批量识别预览同步)
  - [4.6 AI 批量识别预览（异步启动）](#46-ai-批量识别预览异步启动)
  - [4.7 AI 预览任务进度查询](#47-ai-预览任务进度查询)
- [五、图片管理（/api/images）](#五图片管理apiimages)
  - [5.1 上传图片](#51-上传图片)
  - [5.2 上传故事文件](#52-上传故事文件)
  - [5.3 删除图片](#53-删除图片)
- [六、统计信息（/api/stats）](#六统计信息apistats)
  - [6.1 获取统计概览](#61-获取统计概览)
- [七、用户信息（/api/users）](#七用户信息apiusers)
  - [7.1 获取用户信息](#71-获取用户信息)
- [八、特别活动（/api/events）](#八特别活动apievents)
  - [8.1 获取活动列表](#81-获取活动列表)
  - [8.2 创建活动](#82-创建活动)
  - [8.3 删除活动](#83-删除活动)
- [附录 A：纹样编码体系](#附录-a纹样编码体系)
- [附录 B：错误响应格式](#附录-b错误响应格式)
- [附录 C：枚举值参考](#附录-c枚举值参考)

---

## 一、公开 API（/api/open）

以下接口以 `/api/open/` 为前缀，专供外部系统或前端公开页面调用，**仅返回已审核通过（APPROVED）的正式数据**。

### 1.1 纹样图片列表

搜索和浏览已审核通过的纹样图片。

```
GET /api/open/patterns/images
```

**查询参数**（全部可选）：

| 参数 | 类型 | 说明 |
|------|------|------|
| keyword | String | 关键词搜索（匹配纹样描述） |
| mainCategory | String | 主类别代码（2 位，如 `AN`） |
| style | String | 风格代码（2 位，如 `TR`） |
| region | String | 地区代码（2 位，如 `CN`） |
| period | String | 时期代码（2 位，如 `QG`） |
| page | int | 页码（默认 0） |
| size | int | 每页条数（默认 20） |
| sort | String | 排序字段（默认 `createdAt,desc`） |

**请求示例**：

```
GET /api/open/patterns/images?mainCategory=AN&style=TR&page=0&size=10
```

**响应**：`200 OK`

```json
{
  "content": [
    {
      "id": 1,
      "patternCode": "AN-BD-TR-CN-QG-240615-001",
      "description": "秦汉时期传统鸟类纹样",
      "imageUrl": "https://s3.xxx/AN-BD-TR-CN-QG-240615-001.png",
      "mainCategory": "AN",
      "subCategory": "BD",
      "style": "TR",
      "region": "CN",
      "period": "QG"
    }
  ],
  "totalElements": 100,
  "totalPages": 10,
  "size": 10,
  "number": 0
}
```

---

### 1.2 纹样详情

根据纹样编码获取完整详情信息。

```
GET /api/open/patterns/{code}/detail
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| code | String | 纹样编码（如 `AN-BD-TR-CN-QG-240615-001`） |

**请求示例**：

```
GET /api/open/patterns/AN-BD-TR-CN-QG-240615-001/detail
```

**响应** `PatternDetailResponse`：`200 OK`

```json
{
  "id": 1,
  "title": "秦汉传统鸟类纹样",
  "patternCode": "AN-BD-TR-CN-QG-240615-001",
  "image": "https://s3.xxx/AN-BD-TR-CN-QG-240615-001.png",
  "desc": "秦汉时期传统鸟类纹样，描绘飞鸟姿态",
  "story": [
    "此纹样源自秦汉时期的瓦当装饰...",
    "象征自由与高远的意境..."
  ]
}
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 纹样 ID |
| title | String | 纹样标题 |
| patternCode | String | 纹样编码 |
| image | String | 图片 URL |
| desc | String | 纹样描述 |
| story | List\<String\> | 藏品故事（文本按段落拆分） |

---

### 1.3 纹样详情页（HTML）

返回可直接在浏览器中渲染的 HTML 页面，适用于二维码扫码跳转场景。

```
GET /api/open/patterns/{code}/table
Accept: text/html
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| code | String | 纹样编码 |

**响应**：`200 OK`，`Content-Type: text/html`

返回完整的 HTML 页面，包含纹样图片、编码、描述、故事等信息，可直接嵌入 WebView 或浏览器展示。

---

### 1.4 公开藏品列表

获取所有公开可见的数字藏品。

```
GET /api/open/collectibles
```

**请求示例**：

```
GET /api/open/collectibles
```

**响应**：`200 OK`

```json
[
  {
    "id": 1,
    "patternCode": "AN-BD-TR-CN-QG-240615-001",
    "description": "秦汉传统鸟类纹样",
    "imageUrl": "https://s3.xxx/xxx.png",
    "storyText": "藏品故事..."
  }
]
```

> 仅返回 `isVisible=true` 的藏品数据。

---

### 1.5 公开藏品详情

根据藏品 ID 获取详情信息。

```
GET /api/open/collectibles/{id}/detail
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 藏品 ID |

**响应**：同 [1.2 纹样详情](#12-纹样详情) 的 `PatternDetailResponse` 格式。

---

### 1.6 公开活动列表

获取所有特别活动信息。

```
GET /api/open/events
```

**响应**：`200 OK`

```json
[
  {
    "id": 1,
    "title": "传统纹样数字展览",
    "desc": "汇聚百余种中国传统纹样的线上数字展览",
    "image": "https://s3.xxx/event-cover.png",
    "url": "https://example.com/event/1"
  }
]
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 活动 ID |
| title | String | 活动标题 |
| desc | String | 活动描述 |
| image | String | 封面图 URL |
| url | String | 活动详情链接 |

---

## 二、用户认证（/api/auth）

### 2.1 用户注册

注册新用户账号（需要有效邀请码）。

```
POST /api/auth/register
Content-Type: application/json
```

**请求体** `RegisterRequest`：

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|---------|------|
| username | String | 是 | 3-20 字符 | 用户名 |
| password | String | 是 | 至少 6 位 | 密码 |
| confirmPassword | String | 是 | -- | 确认密码（需与 password 一致） |
| invitationCode | String | 是 | 6 位或 8 位数字 | 邀请码或剪艺码 |

**请求示例**：

```json
{
  "username": "zhangsan",
  "password": "123456",
  "confirmPassword": "123456",
  "invitationCode": "123456"
}
```

**响应** `AuthResponse`：`201 Created`

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "message": "注册成功"
}
```

**错误场景**：
- `400` — 参数校验失败（用户名过短、密码过短、邀请码格式错误）
- `409` — 用户名已存在
- `400` — 邀请码无效或已使用
- `400` — 两次密码不一致

---

### 2.2 用户登录

```
POST /api/auth/login
Content-Type: application/json
```

**请求体** `LoginRequest`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 密码 |

**请求示例**：

```json
{
  "username": "admin",
  "password": "admin123"
}
```

**响应** `AuthResponse`：`200 OK`

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "message": "登录成功"
}
```

**错误场景**：
- `400` — 用户名或密码为空
- `401` — 用户名或密码错误

> 登录成功后，后续需要认证的接口请在请求头中携带 `Authorization: Bearer <token>`。

---

### 2.3 忘记密码

```
POST /api/auth/forgot-password
Content-Type: application/json
```

**请求体** `ForgotPasswordRequest`：

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|---------|------|
| username | String | 是 | 3-20 字符 | 用户名 |
| newPassword | String | 是 | 至少 6 位 | 新密码 |
| confirmPassword | String | 是 | -- | 确认新密码 |

**请求示例**：

```json
{
  "username": "zhangsan",
  "newPassword": "654321",
  "confirmPassword": "654321"
}
```

**响应** `AuthResponse`：`200 OK`

```json
{
  "token": null,
  "message": "密码重置成功"
}
```

---

### 2.4 游客登录

以游客身份获取只读 Token，无需任何凭证。

```
POST /api/auth/guest-login
```

无请求体。

**响应** `AuthResponse`：`200 OK`

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "message": "游客登录成功"
}
```

> 游客 Token 权限为只读（GUEST），无法执行写入、删除等操作。

---

## 三、纹样查询与操作（/api/patterns）

### 3.1 创建纹样

直接创建正式纹样（跳过审核流程）。

```
POST /api/patterns
Content-Type: application/json
```

**请求体** `PatternRequest`：

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|---------|------|
| description | String | 否 | -- | 纹样描述 |
| mainCategory | String | 是 | 2 位 | 主类别代码 |
| subCategory | String | 条件 | 2 位 | 子类别代码（AN/PL/PE 必填） |
| style | String | 是 | 2 位 | 风格代码 |
| region | String | 是 | 2 位 | 地区代码 |
| period | String | 是 | 2 位 | 时期代码 |
| imageUrl | String | 是 | -- | 图片 URL |
| imageSourceType | String | 否 | -- | 图片来源（`TEMP_UPLOAD` / `LIBRARY` / `EXTERNAL`） |
| storyText | String | 否 | -- | 藏品故事文字 |
| storyImageUrl | String | 否 | -- | 藏品故事文件 URL |

**请求示例**：

```json
{
  "description": "秦汉传统鸟类纹样",
  "mainCategory": "AN",
  "subCategory": "BD",
  "style": "TR",
  "region": "CN",
  "period": "QG",
  "imageUrl": "https://s3.xxx/temp/xxx.png",
  "imageSourceType": "TEMP_UPLOAD"
}
```

**响应**：`201 Created`，返回完整的 `Pattern` 实体。

---

### 3.2 获取所有纹样

分页查询所有已审核通过的正式纹样。

```
GET /api/patterns?page=0&size=20&sort=createdAt,desc
```

**分页参数**（Spring Data Pageable）：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 0 | 页码（从 0 开始） |
| size | int | 20 | 每页条数 |
| sort | String | createdAt,desc | 排序字段和方向 |

**响应**：`200 OK`，返回 `Page<Pattern>`。

---

### 3.3 根据 ID 查询纹样

```
GET /api/patterns/{id}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 纹样 ID |

**响应**：`200 OK`，返回 `Pattern` 实体。

**`Pattern` 实体字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| patternCode | String | 纹样编码（唯一） |
| mainCategory | String | 主类别代码 |
| subCategory | String | 子类别代码 |
| style | String | 风格代码 |
| region | String | 地区代码 |
| period | String | 时期代码 |
| dateCode | String | 日期代码（YYMMDD） |
| sequenceNumber | Integer | 当日序列号 |
| description | String | 纹样描述 |
| imageUrl | String | 图片 URL |
| imageSourceType | String | 图片来源类型 |
| storyText | String | 藏品故事文字 |
| storyImageUrl | String | 藏品故事文件 URL |
| imageHash | String | 图片 SHA-256 哈希 |
| hashAlgorithm | String | 哈希算法（SHA-256） |
| chainTxHash | String | 区块链交易哈希 |
| chainBlockNumber | Long | 区块高度 |
| chainTimestamp | String | 链上时间 |
| chainStatus | String | 链上状态（PENDING/ANCHORED/FAILED/SKIPPED） |
| status | String | 审核状态 |
| createdAt | String | 创建时间（ISO 8601） |
| updatedAt | String | 更新时间（ISO 8601） |

---

### 3.4 根据编码查询纹样

```
GET /api/patterns/code/{code}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| code | String | 纹样编码（如 `AN-BD-TR-CN-QG-240615-001`） |

**响应**：`200 OK`，返回 `Pattern` 实体。

---

### 3.5 获取纹样二维码（按 ID）

为指定纹样生成唯一二维码图片。

```
GET /api/patterns/{id}/qrcode
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 纹样 ID |

**响应**：`200 OK`

- `Content-Type`: `image/png`
- 返回 PNG 图片二进制流

> 二维码内容为纹样公开详情页 URL，扫码可直接跳转查看纹样信息。

---

### 3.6 获取纹样二维码（按编码）

```
GET /api/patterns/code/{code}/qrcode
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| code | String | 纹样编码 |

**响应**：同 [3.5](#35-获取纹样二维码按-id)，返回 PNG 图片流。

---

### 3.7 更新纹样

```
PUT /api/patterns/{id}
Content-Type: application/json
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 纹样 ID |

**请求体**：同 `PatternRequest`（见 [3.1](#31-创建纹样)）。

**响应**：`200 OK`，返回更新后的 `Pattern` 实体。

---

### 3.8 按主类别查询

```
GET /api/patterns/category/{mainCategory}?page=0&size=20
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| mainCategory | String | 主类别代码（如 `AN`、`PL`、`LA`） |

**分页参数**：同 [3.2](#32-获取所有纹样)

**响应**：`200 OK`，返回 `Page<Pattern>`。

---

### 3.9 按风格查询

```
GET /api/patterns/style/{style}?page=0&size=20
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| style | String | 风格代码（如 `TR` 传统、`MD` 现代） |

**响应**：`200 OK`，返回 `Page<Pattern>`。

---

### 3.10 按地区查询

```
GET /api/patterns/region/{region}?page=0&size=20
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| region | String | 地区代码（如 `CN` 中国） |

**响应**：`200 OK`，返回 `Page<Pattern>`。

---

### 3.11 按时期查询

```
GET /api/patterns/period/{period}?page=0&size=20
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| period | String | 时期代码（如 `QG` 秦汉、`TM` 唐宋） |

**响应**：`200 OK`，返回 `Page<Pattern>`。

---

### 3.12 下载纹样图片（含水印）

下载纹样图片，自动嵌入不可见的 DWT-SVD 鲁棒数字水印。

```
GET /api/patterns/{id}/download
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 纹样 ID |

**响应**：`200 OK`

- `Content-Type`: `image/png`
- 返回嵌入水印后的图片二进制流

> **水印说明**：使用 Haar DWT 小波变换 + SVD 奇异值分解在频域嵌入不可见水印。若水印嵌入失败，将降级返回原图（不影响下载功能）。

---

### 3.13 批量下载纹样图片（含水印）

批量下载多个纹样图片，打包为 ZIP 文件。

```
POST /api/patterns/batch-download
Content-Type: application/json
```

**请求体** `BatchDownloadRequest`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| ids | List\<Long\> | 是 | 纹样 ID 列表 |

**请求示例**：

```json
{
  "ids": [1, 2, 3, 4, 5]
}
```

**响应**：`200 OK`

- `Content-Type`: `application/zip`
- `Content-Disposition`: `attachment; filename="patterns.zip"`
- 返回 ZIP 文件流（每张图片均已嵌入水印）

---

### 3.14 嵌入水印

对任意图片嵌入文本水印。

```
POST /api/patterns/watermark/embed
Content-Type: multipart/form-data
```

**表单参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | 是 | 待嵌入水印的图片文件 |
| text | String | 否 | 水印文本（默认值：`hidden-watermark`） |

**请求示例**（cURL）：

```bash
curl -X POST "https://{your-domain}/api/patterns/watermark/embed" \
  -F "file=@photo.png" \
  -F "text=MyWatermark" \
  --output watermarked.png
```

**响应**：`200 OK`

- `Content-Type`: `image/png`
- 返回嵌入水印后的 PNG 图片

---

### 3.15 验证图片水印

从图片中提取并验证数字水印。

```
POST /api/patterns/watermark/verify
Content-Type: multipart/form-data
```

**表单参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | 是 | 待验证的图片文件 |

**请求示例**（cURL）：

```bash
curl -X POST "https://{your-domain}/api/patterns/watermark/verify" \
  -F "file=@watermarked.png"
```

**响应**：`200 OK`

```json
{
  "watermarkText": "hidden-watermark",
  "verified": true
}
```

**响应字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| watermarkText | String | 提取到的水印文本 |
| verified | boolean | 水印是否验证通过 |

---

## 四、审核记录查询（/api/audit）

### 4.1 获取待审核列表

```
GET /api/audit/pending?page=0&size=20&sort=createdAt,desc
```

**分页参数**：同 [3.2](#32-获取所有纹样)

**响应**：`200 OK`，返回 `Page<PatternPending>`。

**`PatternPending` 主要字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 记录 ID |
| patternCode | String | 纹样编码 |
| mainCategory | String | 主类别代码 |
| subCategory | String | 子类别代码 |
| style | String | 风格代码 |
| region | String | 地区代码 |
| period | String | 时期代码 |
| description | String | 描述 |
| imageUrl | String | 图片 URL |
| imageSourceType | String | 图片来源 |
| status | String | 审核状态（PENDING/APPROVED/REJECTED） |
| rejectReason | String | 拒绝原因 |
| storyText | String | 藏品故事文字 |
| storyImageUrl | String | 藏品故事文件 URL |
| auditTime | String | 审核时间 |
| createdAt | String | 提交时间 |
| updatedAt | String | 更新时间 |

---

### 4.2 获取所有审核记录

```
GET /api/audit?page=0&size=20&sort=createdAt,desc
```

**响应**：`200 OK`，返回 `Page<PatternPending>`（包含所有状态的记录）。

---

### 4.3 按状态查询审核记录

```
GET /api/audit/status/{status}?page=0&size=20
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| status | String | 审核状态（`PENDING` / `APPROVED` / `REJECTED`） |

**响应**：`200 OK`，返回 `Page<PatternPending>`。

---

### 4.4 根据 ID 查询审核记录

```
GET /api/audit/{id}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 审核记录 ID |

**响应**：`200 OK`，返回 `PatternPending` 实体。

---

### 4.5 AI 批量识别预览（同步）

上传图片 URL 列表，AI 自动识别纹样分类信息并返回预览结果。

```
POST /api/audit/ai-batch-preview
Content-Type: application/json
```

**请求体** `AiBatchPreviewRequest`：

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|---------|------|
| imageUrls | List\<String\> | 是 | 非空，元素非空 | 图片 URL 列表 |
| style | String | 否 | 2 位 | 指定风格代码（覆盖 AI 识别结果） |
| region | String | 否 | 2 位 | 指定地区代码 |
| period | String | 否 | 2 位 | 指定时期代码 |
| descriptionPrefix | String | 否 | -- | 描述前缀（拼接在 AI 生成描述前） |

**请求示例**：

```json
{
  "imageUrls": [
    "https://s3.xxx/img1.png",
    "https://s3.xxx/img2.png"
  ],
  "style": "TR",
  "region": "CN",
  "descriptionPrefix": "馆藏纹样"
}
```

**响应** `AiBatchPreviewResponse`：`200 OK`

```json
{
  "total": 2,
  "validCount": 1,
  "invalidCount": 1,
  "items": [
    {
      "imageUrl": "https://s3.xxx/img1.png",
      "patternName": "凤凰纹",
      "description": "传统凤凰纹样，造型优美灵动",
      "mainCategory": "AN",
      "subCategory": "BD",
      "style": "TR",
      "region": "CN",
      "period": "TM",
      "mainCategoryName": "动物",
      "subCategoryName": "鸟类",
      "styleName": "传统",
      "regionName": "中国",
      "periodName": "唐宋",
      "keywords": ["凤凰", "飞鸟", "吉祥"],
      "valid": true,
      "validationErrors": [],
      "error": null
    },
    {
      "imageUrl": "https://s3.xxx/img2.png",
      "patternName": null,
      "description": null,
      "mainCategory": null,
      "valid": false,
      "validationErrors": ["无法识别纹样类别"],
      "error": "AI识别失败：图片内容不清晰"
    }
  ]
}
```

**响应字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| total | int | 总数 |
| validCount | int | 识别有效的数量 |
| invalidCount | int | 识别无效的数量 |
| items | List | 预览结果列表 |

**`AiBatchPreviewItem` 字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| imageUrl | String | 图片 URL |
| patternName | String | AI 识别的纹样名称 |
| description | String | AI 生成的描述 |
| mainCategory | String | 主类别代码 |
| subCategory | String | 子类别代码 |
| style | String | 风格代码 |
| region | String | 地区代码 |
| period | String | 时期代码 |
| mainCategoryName | String | 主类别中文名 |
| subCategoryName | String | 子类别中文名 |
| styleName | String | 风格中文名 |
| regionName | String | 地区中文名 |
| periodName | String | 时期中文名 |
| keywords | List\<String\> | 关键词列表 |
| valid | boolean | 是否有效（编码体系可匹配） |
| validationErrors | List\<String\> | 校验错误列表 |
| error | String | AI 识别错误信息 |

---

### 4.6 AI 批量识别预览（异步启动）

异步版本的 AI 批量识别，适合大批量图片场景。

```
POST /api/audit/ai-batch-preview/start
Content-Type: application/json
```

**请求体**：同 [4.5](#45-ai-批量识别预览同步)

**响应** `AiBatchTaskStartResponse`：`202 Accepted`

```json
{
  "taskId": "a1b2c3d4-e5f6-7890",
  "total": 50,
  "status": "RUNNING"
}
```

**响应字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| taskId | String | 任务 ID（用于查询进度） |
| total | int | 总数 |
| status | String | 任务状态 |

---

### 4.7 AI 预览任务进度查询

查询异步 AI 识别任务的进度和结果。

```
GET /api/audit/ai-batch-preview/progress/{taskId}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| taskId | String | 任务 ID（由 4.6 返回） |

**响应** `AiBatchPreviewTaskProgressResponse`：`200 OK`

```json
{
  "taskId": "a1b2c3d4-e5f6-7890",
  "status": "COMPLETED",
  "total": 50,
  "processed": 50,
  "validCount": 45,
  "invalidCount": 5,
  "progressPercent": 100,
  "completed": true,
  "error": null,
  "startedAtEpochMillis": 1714900000000,
  "finishedAtEpochMillis": 1714900120000,
  "items": []
}
```

**响应字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| taskId | String | 任务 ID |
| status | String | 任务状态（RUNNING / COMPLETED / FAILED） |
| total | int | 总数 |
| processed | int | 已处理数 |
| validCount | int | 有效数 |
| invalidCount | int | 无效数 |
| progressPercent | int | 进度百分比（0-100） |
| completed | boolean | 是否完成 |
| error | String | 任务级错误信息 |
| startedAtEpochMillis | long | 开始时间戳（毫秒） |
| finishedAtEpochMillis | Long | 完成时间戳（毫秒，未完成时为 null） |
| items | List | 预览结果列表（完成后填充） |

---

## 五、图片管理（/api/images）

### 5.1 上传图片

上传图片到对象存储，返回可访问的 URL。

```
POST /api/images/upload
Content-Type: multipart/form-data
```

**表单参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | 是 | 图片文件 |

**请求示例**（cURL）：

```bash
curl -X POST "https://{your-domain}/api/images/upload" \
  -F "file=@photo.png"
```

**响应**：`200 OK`

```json
{
  "url": "https://s3.xxx/temp/abc123.png",
  "message": "上传成功"
}
```

**响应字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| url | String | 图片访问 URL |
| message | String | 提示信息 |

---

### 5.2 上传故事文件

上传藏品故事文件（支持图片和 PDF 格式）。

```
POST /api/images/upload-story-file
Content-Type: multipart/form-data
```

**表单参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | 是 | 故事文件（图片或 PDF） |

**响应**：同 [5.1](#51-上传图片)。

---

### 5.3 删除图片

从对象存储中删除指定图片。

```
DELETE /api/images?url={imageUrl}
```

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| url | String | 是 | 待删除的图片 URL |

**请求示例**：

```
DELETE /api/images?url=https%3A%2F%2Fs3.xxx%2Ftemp%2Fabc123.png
```

**响应**：`200 OK`

```json
{
  "message": "图片删除成功"
}
```

---

## 六、统计信息（/api/stats）

### 6.1 获取统计概览

获取系统运营统计数据。

```
GET /api/stats
```

**响应** `StatsResponse`：`200 OK`

```json
{
  "todaySubmitCount": 15,
  "pendingCount": 42,
  "approvedCount": 1280,
  "totalCount": 1322
}
```

**响应字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| todaySubmitCount | long | 今日提交数量 |
| pendingCount | long | 待审核数量 |
| approvedCount | long | 已审核通过数量 |
| totalCount | long | 纹样总数 |

---

## 七、用户信息（/api/users）

### 7.1 获取用户信息

根据用户 ID 获取公开的用户信息。

```
GET /api/users/{userId}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID |

**响应**：`200 OK`

```json
{
  "id": 1,
  "username": "zhangsan",
  "role": "USER",
  "createdAt": "2026-01-15T10:30:00"
}
```

> 注意：密码等敏感字段不会返回。

---

## 八、特别活动（/api/events）

### 8.1 获取活动列表

```
GET /api/events
```

**响应**：`200 OK`

```json
[
  {
    "id": 1,
    "title": "传统纹样数字展览",
    "desc": "汇聚百余种中国传统纹样的线上数字展览",
    "image": "https://s3.xxx/event-cover.png",
    "url": "https://example.com/event/1"
  }
]
```

---

### 8.2 创建活动

```
POST /api/events
Content-Type: application/json
```

**请求体** `SpecialEventCreateRequest`：

| 字段 | 类型 | 必填 | JSON 别名 | 说明 |
|------|------|------|----------|------|
| title | String | 否 | name | 活动标题 |
| desc | String | 否 | description, summary | 活动描述 |
| image | String | 否 | cover, coverUrl, imageUrl | 封面图 URL |
| url | String | 否 | link | 活动详情链接 |

**请求示例**：

```json
{
  "title": "传统纹样数字展览",
  "desc": "汇聚百余种中国传统纹样的线上数字展览",
  "image": "https://s3.xxx/event-cover.png",
  "url": "https://example.com/event/1"
}
```

> 支持多种 JSON 字段名（别名），如 `name`、`cover`、`link` 等均可被正确解析。

**响应**：`201 Created`，返回 `SpecialEventListItemResponse`。

---

### 8.3 删除活动

```
DELETE /api/events/{id}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 活动 ID |

**响应**：`200 OK`，无返回体。

---

## 附录 A：纹样编码体系

### 主类别代码

| 代码 | 中文名 | 有子类别 | 子类别示例 |
|------|--------|---------|-----------|
| AN | 动物 | 是 | BD(鸟类), BE(兽类), IN(昆虫), AQ(水族) |
| PL | 植物 | 是 | FL(花卉), TR(树木), GR(草本), FR(果实) |
| PE | 人物 | 是 | DT(舞蹈), CT(祭祀), WR(劳动), LG(神话人物) |
| LA | 风景 | 否 | — |
| AB | 抽象 | 否 | — |
| OR | 器物 | 否 | — |
| SY | 符号 | 否 | — |
| CE | 庆典 | 否 | — |
| MY | 神话 | 否 | — |
| OT | 其他 | 否 | — |

### 编码格式

有子类别的主类别（AN/PL/PE）：

```
主类别-子类别-风格-地区-时期-日期(YYMMDD)-序列号(3位)
```

示例：`AN-BD-TR-CN-QG-240615-001`

无子类别的主类别（LA/AB/OR/SY/CE/MY/OT）：

```
主类别-风格1-风格2-地区-时期-日期-序列号
```

### 日期格式

`YYMMDD`，如 `240615` 表示 2024 年 6 月 15 日。

---

## 附录 B：错误响应格式

所有接口统一使用如下错误响应格式：

```json
{
  "timestamp": "2026-05-05T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "具体错误信息",
  "path": "/api/xxx"
}
```

### 常见 HTTP 状态码

| 状态码 | 说明 | 常见原因 |
|--------|------|---------|
| 200 | 成功 | 请求正常处理 |
| 201 | 创建成功 | 资源已创建 |
| 202 | 已接受 | 异步任务已启动 |
| 400 | 请求参数错误 | 参数校验失败、格式错误 |
| 401 | 未认证 | Token 缺失或过期（需认证接口） |
| 403 | 无权限 | 角色权限不足（需认证接口） |
| 404 | 资源不存在 | ID/编码不存在 |
| 409 | 冲突 | 编码重复、用户名已存在 |
| 500 | 服务器内部错误 | 未知异常 |

### 参数校验错误示例

```json
{
  "timestamp": "2026-05-05T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "主类别代码必须为2位",
  "path": "/api/audit/submit"
}
```

---

## 附录 C：枚举值参考

### 用户角色（UserRole）

| 值 | 说明 |
|----|------|
| SUPER_ADMIN | 超级管理员 |
| ADMIN | 管理员 |
| USER | 录入员 |
| GUEST | 游客（只读） |

### 审核状态（AuditStatus）

| 值 | 说明 |
|----|------|
| PENDING | 待审核 |
| APPROVED | 已通过 |
| REJECTED | 已拒绝 |

### 图片来源类型（ImageSourceType）

| 值 | 说明 |
|----|------|
| TEMP_UPLOAD | 临时上传（审核通过后移动到正式目录） |
| LIBRARY | 库内图片（复制到正式目录，保留源文件） |
| EXTERNAL | 外部图片（抓取到正式目录） |

### 区块链存证状态（chainStatus）

| 值 | 说明 |
|----|------|
| PENDING | 待上链 |
| ANCHORED | 已存证 |
| FAILED | 存证失败 |
| SKIPPED | 已跳过（未启用区块链） |

---

> 本文档由系统自动生成，基于源代码 Controller 和 DTO 实际定义整理。如有疑问请联系项目维护者。
