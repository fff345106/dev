# 前端适配指南：图片自动水印

> 编写：蕾姆 | 日期：2026-05-09

## 1. 概述

后端已完成图片自动水印功能。纹样审核通过时，后端会自动嵌入隐形水印并存储两个版本：

- **原图**：`patterns/original/{code}.{ext}`（需认证访问）
- **水印图**：`patterns/watermarked/{code}.{ext}`（公开访问）

API 返回数据中新增 `watermarkedUrl` 字段。前端需要适配以使用水印图 URL 进行展示。

---

## 2. API 变更

### 2.1 新增字段

所有返回纹样数据的 API 端点，响应中新增 `watermarkedUrl` 字段：

```json
{
  "id": 123,
  "patternCode": "AN-BD-TR-YU-QD-260509-001",
  "imageUrl": "https://.../patterns/original/AN-BD-TR-YU-QD-260509-001.png",
  "watermarkedUrl": "https://.../patterns/watermarked/AN-BD-TR-YU-QD-260509-001.png",
  "description": "喜鹊登梅",
  ...
}
```

### 2.2 受影响的 API 端点

| 端点 | 说明 |
|------|------|
| `GET /api/patterns` | 纹样列表 |
| `GET /api/patterns/{id}` | 纹样详情 |
| `GET /api/patterns/code/{code}` | 按编码查询 |
| `GET /api/audit/pending` | 待审核列表 |
| `GET /api/audit` | 所有审核记录 |
| `GET /api/audit/my` | 我的提交记录 |
| `GET /api/audit/my/recent` | 最近录入记录 |

### 2.3 下载 API

`GET /api/patterns/{id}/download` 和 `POST /api/patterns/batch-download` 行为不变，仍返回带水印的图片文件。

---

## 3. 前端改动清单

### 3.1 核心规则

**展示用 `watermarkedUrl`，下载用 `imageUrl`。**

- 列表、网格、详情页、3D 预览等所有 **展示场景**：使用 `watermarkedUrl`
- 下载按钮（已有 Bearer Token 认证）：保持使用 `imageUrl`（原图）

### 3.2 图片 URL 选择函数

建议在工具函数中统一处理图片 URL 选择逻辑。根据项目实际情况，找到 `fixImageUrl` 或类似的图片 URL 处理函数，修改为：

```javascript
/**
 * 获取纹样展示用图片 URL
 * 优先使用水印图 URL，回退到原图 URL
 */
function getDisplayImageUrl(pattern) {
  return pattern.watermarkedUrl || pattern.imageUrl || ''
}

/**
 * 获取纹样下载用图片 URL（原图）
 */
function getDownloadImageUrl(pattern) {
  return pattern.imageUrl || ''
}
```

**回退逻辑说明**：
- 新纹样（2026-05-09 后审核通过）：`watermarkedUrl` 有值
- 历史纹样：`watermarkedUrl` 为 `null`，回退到 `imageUrl`

### 3.3 具体组件改动

#### A. `fixImageUrl` 函数（如存在）

**改动**：优先使用 `watermarkedUrl`

```javascript
// 修改前
function fixImageUrl(url) {
  // ... 处理逻辑
  return url
}

// 修改后
function fixImageUrl(pattern) {
  const url = pattern.watermarkedUrl || pattern.imageUrl || ''
  // ... 原有处理逻辑
  return url
}
```

注意：如果 `fixImageUrl` 只接受 URL 字符串参数，需要调整调用方传入 `watermarkedUrl`。

#### B. `PatternLibrary.vue`（纹样库列表/网格）

**改动位置**：图片展示区域

```vue
<!-- 修改前 -->
<img :src="fixImageUrl(pattern.imageUrl)" />

<!-- 修改后 -->
<img :src="getDisplayImageUrl(pattern)" />
```

#### C. `Pattern3DPreview.vue`（3D 预览）

**改动位置**：纹理加载

```javascript
// 修改前
const textureUrl = pattern.imageUrl

// 修改后
const textureUrl = pattern.watermarkedUrl || pattern.imageUrl
```

#### D. `DigitalCollection.vue`（数字藏品展示）

**改动位置**：藏品图片展示

```vue
<!-- 修改前 -->
<img :src="fixImageUrl(item.imageUrl)" />

<!-- 修改后 -->
<img :src="getDisplayImageUrl(item)" />
```

#### E. 下载功能

**无需改动**。下载功能已通过 Bearer Token 认证访问原图，保持使用 `imageUrl`。

#### F. 审核页面（如适用）

审核员查看的图片可以使用 `watermarkedUrl`（水印不可见，不影响审核）。如果审核页面需要查看原图，保持使用 `imageUrl`。

---

## 4. 兼容性说明

### 4.1 历史数据

- 2026-05-09 之前审核通过的纹样：`watermarkedUrl` 为 `null`
- 前端回退逻辑确保历史纹样仍正常展示（使用 `imageUrl`）

### 4.2 渐进迁移

- 不需要一次性修改所有组件
- 优先修改高频访问的页面（纹样库列表、详情页）
- 其他页面可逐步迁移

---

## 5. 验证要点

| 验证项 | 方法 |
|--------|------|
| 新纹样展示水印图 | 上传新纹样，审核通过后检查图片 URL 是否包含 `watermarked/` |
| 历史纹样正常展示 | 确认旧纹样仍能正常显示（回退到 `imageUrl`） |
| 下载功能正常 | 点击下载按钮，确认返回带水印的图片 |
| 移动端长按保存 | 长按图片保存，使用 `verifyWatermark` API 验证水印存在 |
| 水印不可见 | 对比原图和水印图，肉眼无法区分 |

---

## 6. 水印验证（测试用）

后端提供水印验证 API，可用于测试：

```
POST /api/patterns/watermark/verify
Content-Type: multipart/form-data
Body: file=<图片文件>
```

响应：
```json
{
  "hasWatermark": true,
  "decodedText": "WM:AN-BD-TR-YU-QD-260509-001:42",
  "confidence": 0.95,
  "message": "水印验证成功"
}
```

---

## 7. 无需改动的部分

| 部分 | 原因 |
|------|------|
| 下载 API 调用 | 后端已优化，优先返回水印图 |
| 水印嵌入 API (`embedWatermark`) | 后端自动处理，前端无需调用 |
| 水印验证 API (`verifyWatermark`) | 保持不变 |
| 二维码生成 | 保持不变 |
| 上传流程 | 保持不变 |
