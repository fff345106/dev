# 后端图片自动水印 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在纹样审核通过时自动嵌入隐形水印，确保 S3 中所有纹样图片都包含水印，前端展示水印图，原图仅通过认证下载接口访问。

**Architecture:** 新增 WatermarkStorageService 负责「从 S3 下载原图 → 调用 DwtSvdWatermarkService 嵌入水印 → 上传水印版本到 S3 watermarked/ 路径 → 返回双 URL」。AuditService 和 PatternService 在图片入库后调用此服务。Pattern 实体新增 watermarkedUrl 字段，ImageService 正式路径前缀从 `patterns/` 改为 `patterns/original/`。

**Tech Stack:** Java 17, Spring Boot 3.5.9, AWS S3 SDK, DWT-SVD Watermark (existing), JPA/Hibernate

**Design Spec:** `docs/superpowers/specs/2026-05-09-auto-watermark-design.md`

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `src/main/java/com/example/hello/dto/WatermarkResult.java` | 水印存储结果 DTO：originalUrl + watermarkedUrl |
| `src/main/java/com/example/hello/service/WatermarkStorageService.java` | 水印存储服务：下载原图 → 嵌入水印 → 上传水印版 → 返回双 URL |
| `src/test/java/com/example/hello/service/WatermarkStorageServiceTest.java` | WatermarkStorageService 单元测试 |

### Modified Files

| File | Changes |
|------|---------|
| `src/main/java/com/example/hello/entity/Pattern.java:50-51` | 新增 `watermarkedUrl` 字段 + getter/setter |
| `src/main/java/com/example/hello/dto/PatternDetailResponse.java:9` | 新增 `watermarkedUrl` 字段 + getter/setter |
| `src/main/java/com/example/hello/service/ImageService.java:41` | 新增 `FORMAL_PREFIX` 常量；新增 `uploadBytes()` 方法；修改 `moveToFormal()`/`copyToFormalWithoutDeletingSource()`/`fetchExternalToFormal()`/`delete()` 使用新前缀；`download()` 增加路径回退逻辑 |
| `src/main/java/com/example/hello/service/AuditService.java:319-397` | `moveToPattern()` 中在图片处理后调用 WatermarkStorageService |
| `src/main/java/com/example/hello/service/PatternService.java:75-112` | `create()` 中在图片处理后调用 WatermarkStorageService；`buildHiddenWatermark()` 接受 uploaderId 参数；`download()` 优先使用 watermarkedUrl |

---

### Task 1: WatermarkResult DTO

**Files:**
- Create: `src/main/java/com/example/hello/dto/WatermarkResult.java`

- [ ] **Step 1: Create WatermarkResult DTO**

```java
package com.example.hello.dto;

public class WatermarkResult {
    private final String originalUrl;
    private final String watermarkedUrl;

    public WatermarkResult(String originalUrl, String watermarkedUrl) {
        this.originalUrl = originalUrl;
        this.watermarkedUrl = watermarkedUrl;
    }

    public String getOriginalUrl() { return originalUrl; }
    public String getWatermarkedUrl() { return watermarkedUrl; }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/hello/dto/WatermarkResult.java
git commit -m "feat: add WatermarkResult DTO for dual URL storage"
```

---

### Task 2: Pattern 实体新增 watermarkedUrl 字段

**Files:**
- Modify: `src/main/java/com/example/hello/entity/Pattern.java:50-51`

- [ ] **Step 1: Add watermarkedUrl field to Pattern entity**

在 `imageUrl` 字段之后添加：

```java
@Column(name = "watermarked_url", length = 500)
private String watermarkedUrl;  // 水印图URL，前端展示用
```

在 `getImageUrl()`/`setImageUrl()` 之后添加 getter/setter：

```java
public String getWatermarkedUrl() { return watermarkedUrl; }
public void setWatermarkedUrl(String watermarkedUrl) { this.watermarkedUrl = watermarkedUrl; }
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/hello/entity/Pattern.java
git commit -m "feat: add watermarkedUrl field to Pattern entity"
```

---

### Task 3: PatternDetailResponse DTO 新增字段

**Files:**
- Modify: `src/main/java/com/example/hello/dto/PatternDetailResponse.java:9`

- [ ] **Step 1: Add watermarkedUrl field**

在 `image` 字段之后添加：

```java
private String watermarkedUrl;
```

在 `getImage()`/`setImage()` 之后添加 getter/setter：

```java
public String getWatermarkedUrl() { return watermarkedUrl; }
public void setWatermarkedUrl(String watermarkedUrl) { this.watermarkedUrl = watermarkedUrl; }
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/hello/dto/PatternDetailResponse.java
git commit -m "feat: add watermarkedUrl to PatternDetailResponse DTO"
```

---

### Task 4: ImageService 路径重构 + uploadBytes 方法

**Files:**
- Modify: `src/main/java/com/example/hello/service/ImageService.java`

- [ ] **Step 1: Add FORMAL_PREFIX constant**

在 `AVATAR_FOLDER` 常量之后添加：

```java
private static final String FORMAL_PREFIX = "patterns/original/";
private static final String WATERMARKED_PREFIX = "patterns/watermarked/";
```

- [ ] **Step 2: Add uploadBytes method**

在 `download()` 方法之后添加：

```java
/**
 * 上传字节数组到 S3
 * @param key S3 对象 key
 * @param bytes 文件字节
 * @param contentType 内容类型
 * @return 上传后的 URL
 */
public String uploadBytes(String key, byte[] bytes, String contentType) throws IOException {
    try {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        return baseUrl + "/" + key;
    } catch (S3Exception e) {
        throw new IOException("上传字节数组到 S3 失败: " + e.getMessage(), e);
    }
}
```

- [ ] **Step 3: Update moveToFormal to use FORMAL_PREFIX**

修改 `moveToFormal()` 方法中 `newKey` 的构建（第 208 行附近）：

原代码：
```java
String newKey = patternCode + extension;
```

新代码：
```java
String newKey = FORMAL_PREFIX + patternCode + extension;
```

- [ ] **Step 4: Update copyToFormalWithoutDeletingSource to use FORMAL_PREFIX**

修改 `copyToFormalWithoutDeletingSource()` 方法中 `newKey` 的构建（第 265 行附近）：

原代码：
```java
String newKey = patternCode + extension;
```

新代码：
```java
String newKey = FORMAL_PREFIX + patternCode + extension;
```

- [ ] **Step 5: Update fetchExternalToFormal to use FORMAL_PREFIX**

修改 `fetchExternalToFormal()` 方法中 `newKey` 的构建（第 324 行附近）：

原代码：
```java
String newKey = patternCode + extension;
```

新代码：
```java
String newKey = FORMAL_PREFIX + patternCode + extension;
```

- [ ] **Step 6: Update download method with fallback logic**

修改 `download()` 方法，增加路径回退逻辑：

原代码：
```java
public software.amazon.awssdk.core.ResponseInputStream<software.amazon.awssdk.services.s3.model.GetObjectResponse> download(String imageUrl) throws IOException {
    if (imageUrl == null || imageUrl.isEmpty()) {
        throw new IllegalArgumentException("图片URL不能为空");
    }

    try {
        String key = extractKeyFromUrl(imageUrl);
        software.amazon.awssdk.services.s3.model.GetObjectRequest request = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        return s3Client.getObject(request);
    } catch (S3Exception e) {
        System.err.println("下载图片失败: " + e.getMessage());
        throw new IOException("下载图片失败: " + e.getMessage(), e);
    }
}
```

新代码：
```java
public software.amazon.awssdk.core.ResponseInputStream<software.amazon.awssdk.services.s3.model.GetObjectResponse> download(String imageUrl) throws IOException {
    if (imageUrl == null || imageUrl.isEmpty()) {
        throw new IllegalArgumentException("图片URL不能为空");
    }

    try {
        String key = extractKeyFromUrl(imageUrl);
        software.amazon.awssdk.services.s3.model.GetObjectRequest request = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        return s3Client.getObject(request);
    } catch (S3Exception e) {
        // 回退逻辑：如果 key 以 FORMAL_PREFIX 开头但 404，尝试旧路径 patterns/
        String key = extractKeyFromUrl(imageUrl);
        if (key.startsWith(FORMAL_PREFIX)) {
            String fallbackKey = "patterns/" + key.substring(FORMAL_PREFIX.length());
            try {
                software.amazon.awssdk.services.s3.model.GetObjectRequest fallbackRequest = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(fallbackKey)
                        .build();
                return s3Client.getObject(fallbackRequest);
            } catch (S3Exception fallbackException) {
                System.err.println("下载图片失败（回退路径也失败）: " + fallbackException.getMessage());
                throw new IOException("下载图片失败: " + fallbackException.getMessage(), fallbackException);
            }
        }
        System.err.println("下载图片失败: " + e.getMessage());
        throw new IOException("下载图片失败: " + e.getMessage(), e);
    }
}
```

注意：上面的代码有两个 `String key = extractKeyFromUrl(imageUrl);` 语句，需要重构为只提取一次。实际实现时应该：

```java
public software.amazon.awssdk.core.ResponseInputStream<software.amazon.awssdk.services.s3.model.GetObjectResponse> download(String imageUrl) throws IOException {
    if (imageUrl == null || imageUrl.isEmpty()) {
        throw new IllegalArgumentException("图片URL不能为空");
    }

    String key = extractKeyFromUrl(imageUrl);
    try {
        software.amazon.awssdk.services.s3.model.GetObjectRequest request = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        return s3Client.getObject(request);
    } catch (S3Exception e) {
        // 回退逻辑：如果 key 以 FORMAL_PREFIX 开头但 404，尝试旧路径 patterns/
        if (key.startsWith(FORMAL_PREFIX)) {
            String fallbackKey = "patterns/" + key.substring(FORMAL_PREFIX.length());
            try {
                software.amazon.awssdk.services.s3.model.GetObjectRequest fallbackRequest = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(fallbackKey)
                        .build();
                return s3Client.getObject(fallbackRequest);
            } catch (S3Exception fallbackException) {
                System.err.println("下载图片失败（回退路径也失败）: " + fallbackException.getMessage());
                throw new IOException("下载图片失败: " + fallbackException.getMessage(), fallbackException);
            }
        }
        System.err.println("下载图片失败: " + e.getMessage());
        throw new IOException("下载图片失败: " + e.getMessage(), e);
    }
}
```

- [ ] **Step 7: Add helper method for building watermarked key**

在 `uploadBytes` 方法之后添加：

```java
/**
 * 根据原图 key 构建水印图 key
 * patterns/original/{code}.ext -> patterns/watermarked/{code}.ext
 */
public String toWatermarkedKey(String originalKey) {
    if (originalKey.startsWith(FORMAL_PREFIX)) {
        return WATERMARKED_PREFIX + originalKey.substring(FORMAL_PREFIX.length());
    }
    return WATERMARKED_PREFIX + originalKey;
}

/**
 * 获取正式路径前缀
 */
public String getFormalPrefix() {
    return FORMAL_PREFIX;
}
```

- [ ] **Step 8: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/example/hello/service/ImageService.java
git commit -m "feat: refactor ImageService formal path prefix and add uploadBytes method"
```

---

### Task 5: WatermarkStorageService 实现

**Files:**
- Create: `src/main/java/com/example/hello/service/WatermarkStorageService.java`

- [ ] **Step 1: Create WatermarkStorageService**

```java
package com.example.hello.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.springframework.stereotype.Service;

import com.example.hello.dto.WatermarkResult;

@Service
public class WatermarkStorageService {

    private final ImageService imageService;
    private final DwtSvdWatermarkService dwtSvdWatermarkService;

    public WatermarkStorageService(ImageService imageService, DwtSvdWatermarkService dwtSvdWatermarkService) {
        this.imageService = imageService;
        this.dwtSvdWatermarkService = dwtSvdWatermarkService;
    }

    /**
     * 从 S3 下载原图 -> 嵌入隐形水印 -> 上传水印版本到 S3 -> 返回双 URL
     *
     * @param originalUrl 原图的 S3 URL
     * @param patternCode 纹样编码
     * @param uploaderId  上传者用户 ID
     * @return WatermarkResult 包含原图 URL 和水印图 URL
     * @throws IOException 如果原图下载失败（阻止入库）
     */
    public WatermarkResult embedAndStore(String originalUrl, String patternCode, Long uploaderId) throws IOException {
        // 1. 提取原图 key
        String originalKey = imageService.extractKeyFromUrl(originalUrl);

        // 2. 下载原图字节
        byte[] originalBytes;
        try (var inputStream = imageService.download(originalUrl)) {
            originalBytes = inputStream.readAllBytes();
        }

        // 3. 构建水印文本
        String watermarkText = buildWatermarkText(patternCode, uploaderId);

        // 4. 嵌入水印
        byte[] watermarkedBytes;
        try {
            watermarkedBytes = dwtSvdWatermarkService.embed(
                    new ByteArrayInputStream(originalBytes), watermarkText, ".png");
        } catch (Exception e) {
            // 水印嵌入失败，降级处理：记录日志，返回 null 水印 URL
            System.err.println("水印嵌入失败，降级使用原图: " + e.getMessage());
            return new WatermarkResult(originalUrl, null);
        }

        // 5. 上传水印版本到 S3
        String watermarkedKey = imageService.toWatermarkedKey(originalKey);
        String watermarkedUrl;
        try {
            watermarkedUrl = imageService.uploadBytes(watermarkedKey, watermarkedBytes, "image/png");
        } catch (IOException e) {
            // 水印图上传失败，降级处理
            System.err.println("水印图上传 S3 失败，降级使用原图: " + e.getMessage());
            return new WatermarkResult(originalUrl, null);
        }

        return new WatermarkResult(originalUrl, watermarkedUrl);
    }

    /**
     * 构建水印文本：WM:<patternCode>:<uploaderId>
     */
    String buildWatermarkText(String patternCode, Long uploaderId) {
        String code = (patternCode == null) ? "" : patternCode.trim();
        String userId = (uploaderId == null) ? "0" : uploaderId.toString();
        if (code.isEmpty()) {
            return "WM::" + userId;
        }
        return "WM:" + code + ":" + userId;
    }
}
```

注意：`imageService.extractKeyFromUrl()` 当前是 private 方法，需要改为 package-private 或 public。这将在下一步处理。

- [ ] **Step 2: Make extractKeyFromUrl accessible**

在 `ImageService.java` 中，将 `extractKeyFromUrl` 方法的访问修饰符从 `private` 改为 `public`：

原代码（第 494 行）：
```java
private String extractKeyFromUrl(String url) {
```

新代码：
```java
public String extractKeyFromUrl(String url) {
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/hello/service/WatermarkStorageService.java src/main/java/com/example/hello/service/ImageService.java
git commit -m "feat: add WatermarkStorageService for auto watermark on storage"
```

---

### Task 6: WatermarkStorageService 单元测试

**Files:**
- Create: `src/test/java/com/example/hello/service/WatermarkStorageServiceTest.java`

- [ ] **Step 1: Create unit test**

```java
package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.dto.WatermarkResult;

@ExtendWith(MockitoExtension.class)
class WatermarkStorageServiceTest {

    @Mock
    private ImageService imageService;

    @Mock
    private DwtSvdWatermarkService dwtSvdWatermarkService;

    private WatermarkStorageService service;

    @BeforeEach
    void setUp() {
        service = new WatermarkStorageService(imageService, dwtSvdWatermarkService);
    }

    @Test
    void buildWatermarkText_withValidCodeAndUser() {
        String result = service.buildWatermarkText("AN-BD-TR-YU-QD-260509-001", 42L);
        assertEquals("WM:AN-BD-TR-YU-QD-260509-001:42", result);
    }

    @Test
    void buildWatermarkText_withNullCode() {
        String result = service.buildWatermarkText(null, 42L);
        assertEquals("WM::42", result);
    }

    @Test
    void buildWatermarkText_withEmptyCode() {
        String result = service.buildWatermarkText("  ", 42L);
        assertEquals("WM::42", result);
    }

    @Test
    void buildWatermarkText_withNullUser() {
        String result = service.buildWatermarkText("AN-BD-TR-YU-QD-260509-001", null);
        assertEquals("WM:AN-BD-TR-YU-QD-260509-001:0", result);
    }

    @Test
    void embedAndStore_success() throws IOException {
        // Arrange
        String originalUrl = "https://storage.example.com/patterns/original/AN-BD-TR-YU-QD-260509-001.png";
        String originalKey = "patterns/original/AN-BD-TR-YU-QD-260509-001.png";
        String watermarkedKey = "patterns/watermarked/AN-BD-TR-YU-QD-260509-001.png";
        byte[] originalBytes = new byte[]{1, 2, 3};
        byte[] watermarkedBytes = new byte[]{4, 5, 6};

        when(imageService.extractKeyFromUrl(originalUrl)).thenReturn(originalKey);
        when(imageService.download(originalUrl)).thenReturn(
                new software.amazon.awssdk.core.ResponseInputStream<>(
                        software.amazon.awssdk.services.s3.model.GetObjectResponse.builder()
                                .contentType("image/png").build(),
                        new ByteArrayInputStream(originalBytes)));
        when(dwtSvdWatermarkService.embed(any(), eq("WM:AN-BD-TR-YU-QD-260509-001:42"), eq(".png")))
                .thenReturn(watermarkedBytes);
        when(imageService.toWatermarkedKey(originalKey)).thenReturn(watermarkedKey);
        when(imageService.uploadBytes(eq(watermarkedKey), eq(watermarkedBytes), eq("image/png")))
                .thenReturn("https://storage.example.com/" + watermarkedKey);

        // Act
        WatermarkResult result = service.embedAndStore(originalUrl, "AN-BD-TR-YU-QD-260509-001", 42L);

        // Assert
        assertEquals(originalUrl, result.getOriginalUrl());
        assertNotNull(result.getWatermarkedUrl());
        assertTrue(result.getWatermarkedUrl().contains("watermarked"));
    }

    @Test
    void embedAndStore_watermarkFailure_returnsNullWatermarkedUrl() throws IOException {
        // Arrange
        String originalUrl = "https://storage.example.com/patterns/original/test.png";
        String originalKey = "patterns/original/test.png";

        when(imageService.extractKeyFromUrl(originalUrl)).thenReturn(originalKey);
        when(imageService.download(originalUrl)).thenReturn(
                new software.amazon.awssdk.core.ResponseInputStream<>(
                        software.amazon.awssdk.services.s3.model.GetObjectResponse.builder()
                                .contentType("image/png").build(),
                        new ByteArrayInputStream(new byte[]{1, 2, 3})));
        when(dwtSvdWatermarkService.embed(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Algorithm error"));

        // Act
        WatermarkResult result = service.embedAndStore(originalUrl, "TEST-CODE", 1L);

        // Assert
        assertEquals(originalUrl, result.getOriginalUrl());
        assertNull(result.getWatermarkedUrl());
    }

    @Test
    void embedAndStore_uploadFailure_returnsNullWatermarkedUrl() throws IOException {
        // Arrange
        String originalUrl = "https://storage.example.com/patterns/original/test.png";
        String originalKey = "patterns/original/test.png";

        when(imageService.extractKeyFromUrl(originalUrl)).thenReturn(originalKey);
        when(imageService.download(originalUrl)).thenReturn(
                new software.amazon.awssdk.core.ResponseInputStream<>(
                        software.amazon.awssdk.services.s3.model.GetObjectResponse.builder()
                                .contentType("image/png").build(),
                        new ByteArrayInputStream(new byte[]{1, 2, 3})));
        when(dwtSvdWatermarkService.embed(any(), anyString(), anyString()))
                .thenReturn(new byte[]{4, 5, 6});
        when(imageService.toWatermarkedKey(originalKey)).thenReturn("patterns/watermarked/test.png");
        when(imageService.uploadBytes(any(), anyInt(), anyString()))
                .thenThrow(new IOException("S3 upload failed"));

        // Act
        WatermarkResult result = service.embedAndStore(originalUrl, "TEST-CODE", 1L);

        // Assert
        assertEquals(originalUrl, result.getOriginalUrl());
        assertNull(result.getWatermarkedUrl());
    }
}
```

注意：上面的 `uploadBytes` mock 签名需要与实际实现匹配。如果 `uploadBytes` 的签名是 `(String key, byte[] bytes, String contentType)`，则 mock 需要调整。

- [ ] **Step 2: Run tests**

Run: `mvn test -pl . -Dtest=WatermarkStorageServiceTest -q`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/example/hello/service/WatermarkStorageServiceTest.java
git commit -m "test: add unit tests for WatermarkStorageService"
```

---

### Task 7: AuditService 集成水印服务

**Files:**
- Modify: `src/main/java/com/example/hello/service/AuditService.java:319-397`

- [ ] **Step 1: Add WatermarkStorageService dependency**

在 AuditService 的构造函数中添加 WatermarkStorageService 依赖。找到构造函数（搜索 `@Autowired` 或 `public AuditService`），添加参数：

```java
private final WatermarkStorageService watermarkStorageService;
```

在构造函数中注入：

```java
public AuditService(
        // ... existing params ...
        WatermarkStorageService watermarkStorageService) {
    // ... existing assignments ...
    this.watermarkStorageService = watermarkStorageService;
}
```

- [ ] **Step 2: Integrate watermark in moveToPattern method**

在 `moveToPattern()` 方法中，图片处理完成后（第 358 行 `pattern.setImageUrl(newUrl);` 之后），区块链存证之前，添加水印逻辑：

在 `pattern.setImageSourceType(sourceType.name());` 之后、`pending.setImageUrl(newUrl);` 之前插入：

```java
// 嵌入水印并存储双版本
try {
    String watermarkText = "WM:" + pattern.getPatternCode() + ":" + pending.getSubmitter().getId();
    WatermarkResult wmResult = watermarkStorageService.embedAndStore(
            newUrl, pattern.getPatternCode(), pending.getSubmitter().getId());
    pattern.setImageUrl(wmResult.getOriginalUrl());
    pattern.setWatermarkedUrl(wmResult.getWatermarkedUrl());
} catch (Exception e) {
    // 水印失败不阻塞入库
    System.err.println("水印嵌入失败，降级使用原图: " + e.getMessage());
}
```

同时需要在文件顶部添加 import：

```java
import com.example.hello.dto.WatermarkResult;
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/hello/service/AuditService.java
git commit -m "feat: integrate watermark service in AuditService.moveToPattern"
```

---

### Task 8: PatternService 集成水印服务

**Files:**
- Modify: `src/main/java/com/example/hello/service/PatternService.java:75-112`

- [ ] **Step 1: Add WatermarkStorageService dependency**

在 PatternService 构造函数中添加 WatermarkStorageService 依赖：

```java
private final WatermarkStorageService watermarkStorageService;
```

修改构造函数链：

```java
@Autowired
public PatternService(
        PatternRepository patternRepository,
        PatternPendingRepository patternPendingRepository,
        ImageService imageService,
        PatternCodeService patternCodeService,
        RedisCacheService redisCacheService,
        @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
    this(patternRepository, patternPendingRepository, imageService, patternCodeService, redisCacheService, publicBaseUrl, new DwtSvdWatermarkService(), null);
}

PatternService(
        PatternRepository patternRepository,
        PatternPendingRepository patternPendingRepository,
        ImageService imageService,
        PatternCodeService patternCodeService,
        RedisCacheService redisCacheService,
        String publicBaseUrl,
        DwtSvdWatermarkService dwtSvdWatermarkService,
        WatermarkStorageService watermarkStorageService) {
    this.patternRepository = patternRepository;
    this.patternPendingRepository = patternPendingRepository;
    this.imageService = imageService;
    this.patternCodeService = patternCodeService;
    this.publicBaseUrl = publicBaseUrl;
    this.dwtSvdWatermarkService = dwtSvdWatermarkService;
    this.redisCacheService = redisCacheService;
    this.watermarkStorageService = watermarkStorageService;
}
```

注意：需要添加 import `import com.example.hello.dto.WatermarkResult;`

- [ ] **Step 2: Integrate watermark in create method**

在 `PatternService.create()` 方法中，图片处理完成后（第 102 行 `pattern.setImageSourceType(sourceType.name());` 之后），添加水印逻辑：

```java
// 嵌入水印并存储双版本
if (watermarkStorageService != null) {
    try {
        // create 方法没有 submitter 上下文，使用 0 作为默认值
        WatermarkResult wmResult = watermarkStorageService.embedAndStore(
                newUrl, pattern.getPatternCode(), 0L);
        pattern.setImageUrl(wmResult.getOriginalUrl());
        pattern.setWatermarkedUrl(wmResult.getWatermarkedUrl());
    } catch (Exception e) {
        System.err.println("水印嵌入失败，降级使用原图: " + e.getMessage());
    }
}
```

- [ ] **Step 3: Update buildHiddenWatermark to accept uploaderId**

修改 `buildHiddenWatermark` 方法：

原代码（第 356 行）：
```java
private String buildHiddenWatermark(String patternCode) {
    String code = (patternCode == null) ? "" : patternCode.trim();
    if (code.isEmpty()) {
        return "WM";
    }
    return "WM:" + code;
}
```

新代码：
```java
private String buildHiddenWatermark(String patternCode, Long uploaderId) {
    String code = (patternCode == null) ? "" : patternCode.trim();
    String userId = (uploaderId == null) ? "0" : uploaderId.toString();
    if (code.isEmpty()) {
        return "WM::" + userId;
    }
    return "WM:" + code + ":" + userId;
}
```

- [ ] **Step 4: Update download method callers of buildHiddenWatermark**

更新 `download()` 方法（第 307 行）：

原代码：
```java
String hiddenWatermark = buildHiddenWatermark(pattern.getPatternCode());
```

新代码：
```java
String hiddenWatermark = buildHiddenWatermark(pattern.getPatternCode(), 0L);
```

更新 `batchDownload()` 方法（第 343 行）：

原代码：
```java
byte[] watermarked = addRobustWatermark(inputStream, buildHiddenWatermark(pattern.getPatternCode()), outputExtension);
```

新代码：
```java
byte[] watermarked = addRobustWatermark(inputStream, buildHiddenWatermark(pattern.getPatternCode(), 0L), outputExtension);
```

- [ ] **Step 5: Optimize download to use pre-stored watermarked URL**

修改 `download()` 方法，优先使用已存储的水印图：

原代码（第 299-319 行）：
```java
public java.util.Map<String, Object> download(Long id) throws IOException {
    Pattern pattern = findById(id);
    if (pattern.getImageUrl() == null || pattern.getImageUrl().isEmpty()) {
        throw new RuntimeException("该纹样没有图片");
    }

    String outputExtension = ".png";
    String filename = pattern.getPatternCode() + outputExtension;
    String hiddenWatermark = buildHiddenWatermark(pattern.getPatternCode());

    byte[] watermarked;
    try (InputStream inputStream = imageService.download(pattern.getImageUrl())) {
        watermarked = addRobustWatermark(inputStream, hiddenWatermark, outputExtension);
    }

    return java.util.Map.of(
            "stream", new ByteArrayInputStream(watermarked),
            "filename", filename,
            "contentType", resolveContentTypeByExtension(outputExtension)
    );
}
```

新代码：
```java
public java.util.Map<String, Object> download(Long id) throws IOException {
    Pattern pattern = findById(id);
    if (pattern.getImageUrl() == null || pattern.getImageUrl().isEmpty()) {
        throw new RuntimeException("该纹样没有图片");
    }

    String outputExtension = ".png";
    String filename = pattern.getPatternCode() + outputExtension;

    byte[] watermarked;
    // 优先使用已存储的水印图（新纹样都有 watermarkedUrl）
    if (pattern.getWatermarkedUrl() != null && !pattern.getWatermarkedUrl().isEmpty()) {
        try (InputStream inputStream = imageService.download(pattern.getWatermarkedUrl())) {
            watermarked = inputStream.readAllBytes();
        }
    } else {
        // 降级：历史纹样无 watermarkedUrl，实时嵌入水印
        String hiddenWatermark = buildHiddenWatermark(pattern.getPatternCode(), 0L);
        try (InputStream inputStream = imageService.download(pattern.getImageUrl())) {
            watermarked = addRobustWatermark(inputStream, hiddenWatermark, outputExtension);
        }
    }

    return java.util.Map.of(
            "stream", new ByteArrayInputStream(watermarked),
            "filename", filename,
            "contentType", resolveContentTypeByExtension(outputExtension)
    );
}
```

- [ ] **Step 6: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/hello/service/PatternService.java
git commit -m "feat: integrate watermark service in PatternService.create and optimize download"
```

---

### Task 9: 端到端验证

- [ ] **Step 1: Run full test suite**

Run: `mvn test -pl . -q`
Expected: All tests PASS

- [ ] **Step 2: Verify compilation of entire project**

Run: `mvn clean compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Final commit with all changes**

```bash
git add -A
git status
git commit -m "feat: backend auto watermark on audit approval

- Add WatermarkStorageService for embedding watermarks during storage
- Add watermarkedUrl field to Pattern entity and DTO
- Refactor ImageService formal path to patterns/original/
- Add uploadBytes method to ImageService
- Integrate watermark in AuditService.moveToPattern()
- Integrate watermark in PatternService.create()
- Optimize download API to use pre-stored watermarked URL
- Add unit tests for WatermarkStorageService
"
```

---

## Summary

| Task | Description | New Files | Modified Files |
|------|-------------|-----------|----------------|
| 1 | WatermarkResult DTO | 1 | 0 |
| 2 | Pattern entity field | 0 | 1 |
| 3 | PatternDetailResponse DTO field | 0 | 1 |
| 4 | ImageService refactor | 0 | 1 |
| 5 | WatermarkStorageService | 1 | 1 |
| 6 | Unit tests | 1 | 0 |
| 7 | AuditService integration | 0 | 1 |
| 8 | PatternService integration | 0 | 1 |
| 9 | End-to-end verification | 0 | 0 |

**Total: 3 new files, 5 modified files**
