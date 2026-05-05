# 剪艺 App 用户评价接口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为剪艺 App 新增用户评价功能，允许登录用户提交星级评分和文字评论，支持列表查询、统计展示和管理员管理。

**Architecture:** 遵循项目现有的四层架构（Entity → Repository → Service → Controller），新增 `AppReview` 实体及相关组件。使用 Redis 缓存统计结果，手动 JWT 认证，Jakarta Bean Validation 校验。

**Tech Stack:** Spring Boot 3.x, Spring Data JPA, MySQL 8.x, Redis, Jakarta Bean Validation, JWT (jjwt 0.12.5)

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `src/main/java/com/example/hello/entity/AppReview.java` | 评价实体，映射 `app_reviews` 表 |
| `src/main/java/com/example/hello/repository/AppReviewRepository.java` | 评价数据访问层 |
| `src/main/java/com/example/hello/dto/AppReviewRequest.java` | 提交评价请求 DTO |
| `src/main/java/com/example/hello/dto/AppReviewStatsResponse.java` | 评分统计响应 DTO |
| `src/main/java/com/example/hello/service/AppReviewService.java` | 评价业务逻辑 |
| `src/main/java/com/example/hello/controller/AppReviewController.java` | 评价 REST API |

### Modified Files

| File | Change |
|------|--------|
| `src/main/java/com/example/hello/service/RedisCacheService.java` | 无需修改，直接使用现有 `put/get/evict` 方法 |

---

### Task 1: Create AppReview Entity

**Files:**
- Create: `src/main/java/com/example/hello/entity/AppReview.java`

- [ ] **Step 1: Create AppReview entity**

```java
package com.example.hello.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_reviews")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AppReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User user;

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public AppReview() {}

    public AppReview(User user, Integer rating, String comment) {
        this.user = user;
        this.rating = rating;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/hello/entity/AppReview.java
git commit -m "feat: 添加 AppReview 评价实体"
```

---

### Task 2: Create AppReviewRepository

**Files:**
- Create: `src/main/java/com/example/hello/repository/AppReviewRepository.java`

- [ ] **Step 1: Create AppReviewRepository interface**

```java
package com.example.hello.repository;

import com.example.hello.entity.AppReview;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppReviewRepository extends JpaRepository<AppReview, Long> {

    Page<AppReview> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AppReview> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM AppReview r")
    Double findAverageRating();

    @Query("SELECT COUNT(r) FROM AppReview r")
    long countAll();
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/hello/repository/AppReviewRepository.java
git commit -m "feat: 添加 AppReviewRepository 数据访问层"
```

---

### Task 3: Create DTOs

**Files:**
- Create: `src/main/java/com/example/hello/dto/AppReviewRequest.java`
- Create: `src/main/java/com/example/hello/dto/AppReviewStatsResponse.java`

- [ ] **Step 1: Create AppReviewRequest DTO**

```java
package com.example.hello.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AppReviewRequest {

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分必须在 1-5 之间")
    @Max(value = 5, message = "评分必须在 1-5 之间")
    private Integer rating;

    @Size(max = 500, message = "评论最长 500 字符")
    private String comment;

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
```

- [ ] **Step 2: Create AppReviewStatsResponse DTO**

```java
package com.example.hello.dto;

public class AppReviewStatsResponse {

    private Double averageRating;
    private Long totalReviews;

    public AppReviewStatsResponse() {}

    public AppReviewStatsResponse(Double averageRating, Long totalReviews) {
        this.averageRating = averageRating;
        this.totalReviews = totalReviews;
    }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }
    public Long getTotalReviews() { return totalReviews; }
    public void setTotalReviews(Long totalReviews) { this.totalReviews = totalReviews; }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/hello/dto/AppReviewRequest.java src/main/java/com/example/hello/dto/AppReviewStatsResponse.java
git commit -m "feat: 添加评价相关 DTO（AppReviewRequest、AppReviewStatsResponse）"
```

---

### Task 4: Create AppReviewService

**Files:**
- Create: `src/main/java/com/example/hello/service/AppReviewService.java`

- [ ] **Step 1: Create AppReviewService**

```java
package com.example.hello.service;

import java.time.Duration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.dto.AppReviewStatsResponse;
import com.example.hello.entity.AppReview;
import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.AppReviewRepository;
import com.example.hello.repository.UserRepository;

@Service
public class AppReviewService {

    private static final String STATS_CACHE_KEY = "reviews::stats";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final AppReviewRepository appReviewRepository;
    private final UserRepository userRepository;
    private final RedisCacheService redisCacheService;

    public AppReviewService(AppReviewRepository appReviewRepository,
                            UserRepository userRepository,
                            RedisCacheService redisCacheService) {
        this.appReviewRepository = appReviewRepository;
        this.userRepository = userRepository;
        this.redisCacheService = redisCacheService;
    }

    @Transactional
    public AppReview submitReview(Long userId, Integer rating, String comment) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (rating < 1 || rating > 5) {
            throw new RuntimeException("评分必须在 1-5 之间");
        }

        AppReview review = new AppReview(user, rating, comment);
        AppReview saved = appReviewRepository.save(review);

        // 清除统计缓存
        redisCacheService.evict(STATS_CACHE_KEY);

        return saved;
    }

    public Page<AppReview> getReviews(Pageable pageable) {
        return appReviewRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public AppReviewStatsResponse getStats() {
        // 尝试从缓存获取
        AppReviewStatsResponse cached = redisCacheService.get(STATS_CACHE_KEY, AppReviewStatsResponse.class);
        if (cached != null) {
            return cached;
        }

        // 从数据库计算
        Double average = appReviewRepository.findAverageRating();
        long total = appReviewRepository.countAll();

        AppReviewStatsResponse stats = new AppReviewStatsResponse(
                average != null ? Math.round(average * 10.0) / 10.0 : 0.0,
                total
        );

        // 写入缓存
        redisCacheService.put(STATS_CACHE_KEY, stats, CACHE_TTL);

        return stats;
    }

    public Page<AppReview> getMyReviews(Long userId, Pageable pageable) {
        return appReviewRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public void deleteReview(Long reviewId, UserRole operatorRole) {
        if (operatorRole != UserRole.ADMIN && operatorRole != UserRole.SUPER_ADMIN) {
            throw new RuntimeException("仅管理员可以删除评价");
        }

        AppReview review = appReviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("评价不存在"));

        appReviewRepository.delete(review);

        // 清除统计缓存
        redisCacheService.evict(STATS_CACHE_KEY);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/hello/service/AppReviewService.java
git commit -m "feat: 添加 AppReviewService 评价业务逻辑"
```

---

### Task 5: Create AppReviewController

**Files:**
- Create: `src/main/java/com/example/hello/controller/AppReviewController.java`

- [ ] **Step 1: Create AppReviewController**

```java
package com.example.hello.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.AppReviewRequest;
import com.example.hello.dto.AppReviewStatsResponse;
import com.example.hello.entity.AppReview;
import com.example.hello.enums.UserRole;
import com.example.hello.service.AppReviewService;
import com.example.hello.util.JwtUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reviews")
public class AppReviewController {

    private final AppReviewService appReviewService;
    private final JwtUtil jwtUtil;

    public AppReviewController(AppReviewService appReviewService, JwtUtil jwtUtil) {
        this.appReviewService = appReviewService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 提交评价
     */
    @PostMapping
    public ResponseEntity<?> submitReview(
            @Valid @RequestBody AppReviewRequest request,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = getUserIdFromToken(token);
            AppReview review = appReviewService.submitReview(userId, request.getRating(), request.getComment());
            return ResponseEntity.status(HttpStatus.CREATED).body(review);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 评价列表（分页，按时间倒序）
     */
    @GetMapping
    public ResponseEntity<Page<AppReview>> getReviews(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(appReviewService.getReviews(pageable));
    }

    /**
     * 平均评分统计
     */
    @GetMapping("/stats")
    public ResponseEntity<AppReviewStatsResponse> getStats() {
        return ResponseEntity.ok(appReviewService.getStats());
    }

    /**
     * 我的评价列表
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyReviews(
            @RequestHeader("Authorization") String token,
            @PageableDefault(size = 20) Pageable pageable) {
        try {
            Long userId = getUserIdFromToken(token);
            return ResponseEntity.ok(appReviewService.getMyReviews(userId, pageable));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 删除评价（仅管理员）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        try {
            String jwt = token.replace("Bearer ", "");
            UserRole role = UserRole.valueOf(jwtUtil.extractRole(jwt));
            appReviewService.deleteReview(id, role);
            return ResponseEntity.ok(Map.of("message", "评价删除成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/hello/controller/AppReviewController.java
git commit -m "feat: 添加 AppReviewController 评价 REST API"
```

---

### Task 6: Verify Build and Manual Test

- [ ] **Step 1: Compile the project**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run the application (optional, for manual testing)**

Run: `mvn spring-boot:run`
Expected: Application starts on port 8080

- [ ] **Step 3: Commit all files together if needed**

```bash
git status
```

Verify all 6 new files are tracked:
- `entity/AppReview.java`
- `repository/AppReviewRepository.java`
- `dto/AppReviewRequest.java`
- `dto/AppReviewStatsResponse.java`
- `service/AppReviewService.java`
- `controller/AppReviewController.java`
