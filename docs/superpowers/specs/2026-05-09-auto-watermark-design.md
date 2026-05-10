# 后端图片自动水印设计规格

> 编写：蕾姆 | 日期：2026-05-09 | 状态：已确认

---

## 1. 问题背景

当前系统的图片水印方案存在以下缺陷：

- **前端防护不足**：`@contextmenu.prevent` 只能拦截 PC 右键菜单，移动端长按图片可直接保存原图
- **水印嵌入时机过晚**：隐形水印仅在用户点击"下载"按钮时才通过 `embedWatermark` API 嵌入，移动端用户绕过下载流程即可获取无水印原图
- **图片 URL 完全公开**：对象存储 URL 无需认证即可直接访问，任何人拿到 URL 就能下载原图

**核心诉求**：用户在任何途径获取到的图片都应带有隐形水印。

---

## 2. 设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 嵌入时机 | 审核通过时 | 审核员看到原图，水印内容可包含确定信息 |
| 水印内容 | `WM:<patternCode>:<uploaderId>` | 简洁且可溯源到上传者 |
| 处理范围 | 仅纹样主图 | 故事文件非核心资产 |
| 存储策略 | 双路径（original/ + watermarked/） | 原图不丢失，职责清晰 |
| API 方案 | 双 URL 字段 | 前端灵活，可渐进迁移 |
| 架构方案 | 独立 WatermarkStorageService | 职责清晰，可独立测试 |

---

## 3. 核心架构

### 3.1 新增组件：WatermarkStorageService

职责：从 S3 读取原图 -> 嵌入隐形水印 -> 上传水印版本到 S3 -> 返回双 URL

```
调用方 (AuditService / PatternService)
    |
    v
WatermarkStorageService.embedAndStore(originalKey, watermarkText)
    |
    +-- 1. ImageService.download(originalKey) -> 原图字节
    |
    +-- 2. DwtSvdWatermarkService.embed(原图, 水印文本) -> 水印图字节
    |
    +-- 3. ImageService.uploadBytes(watermarkedKey, 水印图字节)
    |
    +-- 4. 返回 WatermarkResult { originalUrl, watermarkedUrl }
```

### 3.2 水印文本格式

```
WM:<patternCode>:<uploaderId>
```

示例：`WM:AN-BD-TR-YU-QD-260509-001:42`

编码方式：文本 -> 32x32 QR 码 -> 1024 bit -> 循环填充至 LL 子带容量（复用现有 DWT-SVD 算法）。

### 3.3 辅助类：WatermarkResult

```java
public class WatermarkResult {
    private String originalUrl;    // 原图 URL
    private String watermarkedUrl; // 水印图 URL
}
```

---

## 4. 数据模型变更

### 4.1 Pattern 实体

新增字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `watermarkedUrl` | String(500) | 水印图 URL，前端展示用，可为 null |

### 4.2 PatternDetailResponse DTO

新增对应字段 `watermarkedUrl`。

### 4.3 数据库迁移

`patterns` 表新增 `watermarked_url VARCHAR(500)` 列，允许 NULL（兼容历史数据）。

### 4.4 S3 路径变更影响

当前正式图片存储路径为 `patterns/{patternCode}.{ext}`，改造后变为 `patterns/original/{patternCode}.{ext}`。

受影响的 `ImageService` 方法：

| 方法 | 变更 |
|------|------|
| `moveToFormal()` | 目标 key 从 `patterns/{code}.{ext}` 改为 `patterns/original/{code}.{ext}` |
| `copyToFormalWithoutDeletingSource()` | 同上 |
| `fetchExternalToFormal()` | 同上 |
| `delete()` | 删除路径同步更新 |
| `download()` | 下载路径同步更新 |

**兼容策略**：`ImageService` 中的正式路径前缀从 `patterns/` 改为 `patterns/original/`，统一由常量 `FORMAL_PREFIX = "patterns/original/"` 管理。历史数据仍存储在 `patterns/` 路径下，下载时优先查找 `patterns/original/`，回退到 `patterns/`。

---

## 5. S3 路径规划

```
hpy8jg7h-images/
+-- temp/                          # 临时上传（不变）
|   +-- {uuid}.png
+-- patterns/                      # 正式纹样（新结构）
|   +-- original/                  # 原图
|   |   +-- {patternCode}.{ext}
|   +-- watermarked/               # 水印图
|       +-- {patternCode}.{ext}
+-- stories/                       # 故事文件（不变）
    +-- ...
```

---

## 6. 集成流程

### 6.1 审核通过流程（AuditService.moveToPattern）

```
1. 原有逻辑：根据来源类型移动图片到正式目录
   +-- TEMP_UPLOAD -> moveToFormal()
   +-- LIBRARY     -> copyToFormalWithoutDeletingSource()
   +-- EXTERNAL    -> fetchExternalToFormal()

2. 【新增】嵌入水印并存储双版本
   +-- watermarkStorageService.embedAndStore(formalKey, watermarkText)
   |   +-- 下载正式目录中的原图
   |   +-- 嵌入水印 "WM:<patternCode>:<submitterId>"
   |   +-- 上传到 watermarked/ 路径
   +-- pattern.setImageUrl(originalUrl)
   +-- pattern.setWatermarkedUrl(watermarkedUrl)

3. 原有逻辑：计算 SHA-256 哈希（基于原图）
4. 原有逻辑：触发区块链存证
```

### 6.2 直接创建流程（PatternService.create）

```
1. 原有逻辑：移动/复制/抓取图片到正式目录
2. 【新增】调用 watermarkStorageService.embedAndStore()
3. 原有逻辑：保存 Pattern 实体
```

### 6.3 异常处理策略

| 场景 | 处理方式 |
|------|----------|
| 水印嵌入失败（算法异常） | 记录错误日志，watermarkedUrl 设为 null，降级使用原图 URL |
| 水印图上传 S3 失败 | 同上，降级处理 |
| 原图下载失败 | 抛出异常，阻止入库（原图必须存在） |

**核心原则**：水印失败不阻塞入库，但必须记录日志以便排查。

---

## 7. API 变更

### 7.1 Pattern 实体返回数据

```json
{
  "id": 123,
  "patternCode": "AN-BD-TR-YU-QD-260509-001",
  "imageUrl": "https://.../patterns/original/AN-BD-TR-YU-QD-260509-001.png",
  "watermarkedUrl": "https://.../patterns/watermarked/AN-BD-TR-YU-QD-260509-001.png"
}
```

### 7.2 下载 API 变化

当前 `GET /api/patterns/{id}/download` 在下载时动态嵌入水印。改造后：

- 如果 `watermarkedUrl` 存在，可直接返回水印图（跳过实时嵌入，提升性能）
- 保留动态嵌入作为降级方案（兼容历史数据）

---

## 8. 前端适配要点

| 组件 | 改动 |
|------|------|
| `fixImageUrl()` | 优先使用 `watermarkedUrl`，回退到 `imageUrl` |
| `PatternLibrary.vue` | 列表/网格展示使用水印图 URL |
| `Pattern3DPreview.vue` | 纹理加载使用水印图 URL |
| `DigitalCollection.vue` | 藏品展示使用水印图 URL |
| 下载功能 | 保持使用 `imageUrl`（原图，已有认证保护） |

### 历史数据兼容

- 新增字段 `watermarkedUrl` 允许 null
- 前端逻辑：`watermarkedUrl || imageUrl` 回退机制
- 历史纹样仍展示原图，不受影响
- 可选：后续编写批量迁移脚本，为历史纹样补嵌水印

---

## 9. 测试策略

| 测试类型 | 覆盖内容 |
|----------|----------|
| 单元测试 | WatermarkStorageService：Mock ImageService 和 DwtSvdWatermarkService，验证流程编排 |
| 单元测试 | 水印文本生成：验证 `WM:<code>:<userId>` 格式正确 |
| 集成测试 | 审核通过后验证 watermarkedUrl 非空且可访问 |
| 集成测试 | 从水印图中提取水印，验证内容与嵌入一致 |
| 回归测试 | 下载 API 仍能正常工作（原图下载 + 水印嵌入） |

---

## 10. 边界情况

| 场景 | 处理 |
|------|------|
| 历史纹样无 watermarkedUrl | 前端回退到 imageUrl |
| 非图片文件（PDF 故事） | 不处理水印，跳过 |
| 超大图片（>10MB） | DWT-SVD 处理可能较慢，考虑异步处理或超时保护 |
| EXTERNAL 来源图片下载失败 | 阻止入库，抛出异常 |
| 水印嵌入后图片质量下降 | DWT-SVD 已在频域操作，肉眼不可见，当前算法已验证 |

---

## 11. 验收标准

- [ ] 上传纹样图片后，对象存储中同时存在原图和带水印版本
- [ ] 带水印版本与原图肉眼无法区分（水印不可见）
- [ ] 通过 verifyWatermark API 可从水印版本中正确提取水印信息
- [ ] 水印内容为 `WM:<patternCode>:<uploaderId>` 格式
- [ ] 前端展示的图片 URL 指向带水印版本
- [ ] 直接访问水印图 URL 无需认证即可加载
- [ ] 原图 URL 需要认证才能访问
- [ ] 移动端长按保存的图片包含隐形水印
- [ ] 水印嵌入失败时系统降级正常运行，不阻塞入库

---

## 12. 文件变更清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `WatermarkStorageService.java` | 水印存储服务 |
| `WatermarkResult.java` | 水印结果 DTO |
| `WatermarkStorageServiceTest.java` | 单元测试 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `Pattern.java` | 新增 `watermarkedUrl` 字段 |
| `PatternDetailResponse.java` | 新增 `watermarkedUrl` 字段 |
| `AuditService.java` | `moveToPattern()` 中调用水印服务 |
| `PatternService.java` | `create()` 中调用水印服务 |
| `ImageService.java` | 新增 `uploadBytes(key, bytes)` 方法（上传字节数组到 S3）；正式路径前缀从 `patterns/` 改为 `patterns/original/`；下载方法增加路径回退逻辑 |

---

## 13. 时间估算

| 阶段 | 工作内容 | 预估工时 |
|------|----------|----------|
| 后端 | WatermarkStorageService + 数据模型变更 | 0.5 天 |
| 后端 | AuditService / PatternService 集成 | 0.5 天 |
| 后端 | 单元测试 + 集成测试 | 0.5 天 |
| 前端 | 适配新字段、切换图片源 | 0.5 天 |
| 联调 | 端到端测试水印嵌入和提取 | 0.5 天 |

**总计：约 2.5 天**
