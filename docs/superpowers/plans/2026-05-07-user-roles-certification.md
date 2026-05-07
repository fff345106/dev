# 用户角色扩展与认证系统实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 扩展用户角色体系（普通用户、企商用户、技艺大师），引入实名认证和角色认证机制，实现权限控制。

**Architecture:** 新增 UserCertification 实体存储认证信息，通过 UserPermissionService 进行权限检查，CertificationController/CertificationService 处理认证流程。复用现有 ImageService 处理文件上传，复用 InvitationCodeService 处理剪艺码验证。

**Tech Stack:** Spring Boot 3.5.9, Spring Data JPA, MySQL 8.x, AWS S3 SDK, Jakarta Bean Validation

---

## 文件结构

### 新增文件

| 文件路径 | 职责 |
|---------|------|
| `src/main/java/com/example/hello/enums/CertificationType.java` | 认证类型枚举（REAL_NAME/ENTERPRISE/MASTER） |
| `src/main/java/com/example/hello/enums/CertificationStatus.java` | 认证状态枚举（PENDING/APPROVED/REJECTED） |
| `src/main/java/com/example/hello/entity/UserCertification.java` | 用户认证实体 |
| `src/main/java/com/example/hello/repository/UserCertificationRepository.java` | 认证数据仓库 |
| `src/main/java/com/example/hello/dto/RealNameAuthRequest.java` | 实名认证请求 DTO |
| `src/main/java/com/example/hello/dto/EnterpriseAuthRequest.java` | 企业认证请求 DTO |
| `src/main/java/com/example/hello/dto/MasterAuthRequest.java` | 技艺认证请求 DTO |
| `src/main/java/com/example/hello/dto/CertificationResponse.java` | 认证状态响应 DTO |
| `src/main/java/com/example/hello/dto/CertificationAuditRequest.java` | 认证审核请求 DTO |
| `src/main/java/com/example/hello/service/CertificationService.java` | 认证业务服务 |
| `src/main/java/com/example/hello/service/UserPermissionService.java` | 权限检查服务 |
| `src/main/java/com/example/hello/controller/CertificationController.java` | 认证 API 控制器 |
| `src/test/java/com/example/hello/service/CertificationServiceTest.java` | 认证服务单元测试 |
| `src/test/java/com/example/hello/service/UserPermissionServiceTest.java` | 权限服务单元测试 |
| `src/test/java/com/example/hello/controller/CertificationControllerTest.java` | 认证控制器测试 |

### 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `src/main/java/com/example/hello/enums/UserRole.java` | 新增 REGULAR_USER/ENTERPRISE_USER/MASTER_ARTISAN 枚举值 |
| `src/main/java/com/example/hello/dto/RegisterRequest.java` | 新增 roleType 字段 |
| `src/main/java/com/example/hello/service/AuthService.java` | 改造注册流程，支持角色选择 |
| `src/main/java/com/example/hello/controller/AuthController.java` | 适配新的注册流程 |
| `src/test/java/com/example/hello/service/AuthServiceTest.java` | 更新注册测试用例 |

---

## Task 1: 新增枚举类型

**Files:**
- Create: `src/main/java/com/example/hello/enums/CertificationType.java`
- Create: `src/main/java/com/example/hello/enums/CertificationStatus.java`
- Modify: `src/main/java/com/example/hello/enums/UserRole.java`

- [ ] **Step 1: 创建 CertificationType 枚举**

```java
package com.example.hello.enums;

public enum CertificationType {
    REAL_NAME("实名认证"),
    ENTERPRISE("企业认证"),
    MASTER("技艺认证");

    private final String name;

    CertificationType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
```

- [ ] **Step 2: 创建 CertificationStatus 枚举**

```java
package com.example.hello.enums;

public enum CertificationStatus {
    PENDING("待审核"),
    APPROVED("已通过"),
    REJECTED("已拒绝");

    private final String name;

    CertificationStatus(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
```

- [ ] **Step 3: 扩展 UserRole 枚举**

在 `UserRole.java` 中添加三个新角色：

```java
package com.example.hello.enums;

public enum UserRole {
    SUPER_ADMIN("超级管理员"),
    ADMIN("管理员"),
    USER("录入员"),
    GUEST("游客"),
    REGULAR_USER("普通用户"),
    ENTERPRISE_USER("企商用户"),
    MASTER_ARTISAN("技艺大师");

    private final String name;

    UserRole(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/hello/enums/CertificationType.java \
        src/main/java/com/example/hello/enums/CertificationStatus.java \
        src/main/java/com/example/hello/enums/UserRole.java
git commit -m "feat: 新增认证类型/状态枚举，扩展用户角色枚举"
```

---

## Task 2: 创建 UserCertification 实体

**Files:**
- Create: `src/main/java/com/example/hello/entity/UserCertification.java`
- Create: `src/main/java/com/example/hello/repository/UserCertificationRepository.java`

- [ ] **Step 1: 创建 UserCertification 实体**

```java
package com.example.hello.entity;

import java.time.LocalDateTime;

import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_certifications")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class UserCertification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "certification_type", nullable = false)
    private CertificationType certificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificationStatus status = CertificationStatus.PENDING;

    // 实名认证字段
    @Column(name = "real_name", length = 50)
    private String realName;

    @Column(name = "id_card_number", length = 18)
    private String idCardNumber;

    @Column(name = "id_card_front_url", length = 500)
    private String idCardFrontUrl;

    @Column(name = "id_card_back_url", length = 500)
    private String idCardBackUrl;

    @Column(name = "real_name_verified")
    private Boolean realNameVerified = false;

    // 企业认证字段
    @Column(name = "business_license_url", length = 500)
    private String businessLicenseUrl;

    @Column(name = "authorization_letter_url", length = 500)
    private String authorizationLetterUrl;

    @Column(name = "legal_representative_name", length = 50)
    private String legalRepresentativeName;

    @Column(name = "is_legal_representative")
    private Boolean isLegalRepresentative;

    // 技艺认证字段
    @Column(name = "certification_url", length = 500)
    private String certificationUrl;

    @Column(name = "representative_work_url", length = 500)
    private String representativeWorkUrl;

    // 审核信息
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "auditor_id")
    private Long auditorId;

    @Column(name = "audit_time")
    private LocalDateTime auditTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserCertification() {}

    public UserCertification(User user, CertificationType certificationType) {
        this.user = user;
        this.certificationType = certificationType;
        this.status = CertificationStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public CertificationType getCertificationType() { return certificationType; }
    public void setCertificationType(CertificationType certificationType) { this.certificationType = certificationType; }
    public CertificationStatus getStatus() { return status; }
    public void setStatus(CertificationStatus status) { this.status = status; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getIdCardNumber() { return idCardNumber; }
    public void setIdCardNumber(String idCardNumber) { this.idCardNumber = idCardNumber; }
    public String getIdCardFrontUrl() { return idCardFrontUrl; }
    public void setIdCardFrontUrl(String idCardFrontUrl) { this.idCardFrontUrl = idCardFrontUrl; }
    public String getIdCardBackUrl() { return idCardBackUrl; }
    public void setIdCardBackUrl(String idCardBackUrl) { this.idCardBackUrl = idCardBackUrl; }
    public Boolean getRealNameVerified() { return realNameVerified; }
    public void setRealNameVerified(Boolean realNameVerified) { this.realNameVerified = realNameVerified; }
    public String getBusinessLicenseUrl() { return businessLicenseUrl; }
    public void setBusinessLicenseUrl(String businessLicenseUrl) { this.businessLicenseUrl = businessLicenseUrl; }
    public String getAuthorizationLetterUrl() { return authorizationLetterUrl; }
    public void setAuthorizationLetterUrl(String authorizationLetterUrl) { this.authorizationLetterUrl = authorizationLetterUrl; }
    public String getLegalRepresentativeName() { return legalRepresentativeName; }
    public void setLegalRepresentativeName(String legalRepresentativeName) { this.legalRepresentativeName = legalRepresentativeName; }
    public Boolean getIsLegalRepresentative() { return isLegalRepresentative; }
    public void setIsLegalRepresentative(Boolean isLegalRepresentative) { this.isLegalRepresentative = isLegalRepresentative; }
    public String getCertificationUrl() { return certificationUrl; }
    public void setCertificationUrl(String certificationUrl) { this.certificationUrl = certificationUrl; }
    public String getRepresentativeWorkUrl() { return representativeWorkUrl; }
    public void setRepresentativeWorkUrl(String representativeWorkUrl) { this.representativeWorkUrl = representativeWorkUrl; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Long getAuditorId() { return auditorId; }
    public void setAuditorId(Long auditorId) { this.auditorId = auditorId; }
    public LocalDateTime getAuditTime() { return auditTime; }
    public void setAuditTime(LocalDateTime auditTime) { this.auditTime = auditTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: 创建 UserCertificationRepository**

```java
package com.example.hello.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hello.entity.User;
import com.example.hello.entity.UserCertification;
import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;

public interface UserCertificationRepository extends JpaRepository<UserCertification, Long> {
    Optional<UserCertification> findByUserAndCertificationType(User user, CertificationType certificationType);
    List<UserCertification> findByStatus(CertificationStatus status);
    List<UserCertification> findByUser(User user);
    boolean existsByUserAndCertificationTypeAndStatus(User user, CertificationType certificationType, CertificationStatus status);
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/example/hello/entity/UserCertification.java \
        src/main/java/com/example/hello/repository/UserCertificationRepository.java
git commit -m "feat: 创建 UserCertification 实体和仓库"
```

---

## Task 3: 创建认证相关 DTO

**Files:**
- Create: `src/main/java/com/example/hello/dto/RealNameAuthRequest.java`
- Create: `src/main/java/com/example/hello/dto/EnterpriseAuthRequest.java`
- Create: `src/main/java/com/example/hello/dto/MasterAuthRequest.java`
- Create: `src/main/java/com/example/hello/dto/CertificationResponse.java`
- Create: `src/main/java/com/example/hello/dto/CertificationAuditRequest.java`

- [ ] **Step 1: 创建 RealNameAuthRequest**

```java
package com.example.hello.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RealNameAuthRequest {
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50")
    private String realName;

    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "\\d{17}[\\dXx]", message = "身份证号格式不正确")
    private String idCardNumber;

    @NotBlank(message = "身份证正面照片不能为空")
    private String idCardFrontUrl;

    @NotBlank(message = "身份证背面照片不能为空")
    private String idCardBackUrl;

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getIdCardNumber() { return idCardNumber; }
    public void setIdCardNumber(String idCardNumber) { this.idCardNumber = idCardNumber; }
    public String getIdCardFrontUrl() { return idCardFrontUrl; }
    public void setIdCardFrontUrl(String idCardFrontUrl) { this.idCardFrontUrl = idCardFrontUrl; }
    public String getIdCardBackUrl() { return idCardBackUrl; }
    public void setIdCardBackUrl(String idCardBackUrl) { this.idCardBackUrl = idCardBackUrl; }
}
```

- [ ] **Step 2: 创建 EnterpriseAuthRequest**

```java
package com.example.hello.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EnterpriseAuthRequest {
    @NotBlank(message = "营业执照照片不能为空")
    private String businessLicenseUrl;

    private String authorizationLetterUrl;

    @NotBlank(message = "法人姓名不能为空")
    @Size(max = 50, message = "法人姓名长度不能超过50")
    private String legalRepresentativeName;

    private Boolean isLegalRepresentative;

    public String getBusinessLicenseUrl() { return businessLicenseUrl; }
    public void setBusinessLicenseUrl(String businessLicenseUrl) { this.businessLicenseUrl = businessLicenseUrl; }
    public String getAuthorizationLetterUrl() { return authorizationLetterUrl; }
    public void setAuthorizationLetterUrl(String authorizationLetterUrl) { this.authorizationLetterUrl = authorizationLetterUrl; }
    public String getLegalRepresentativeName() { return legalRepresentativeName; }
    public void setLegalRepresentativeName(String legalRepresentativeName) { this.legalRepresentativeName = legalRepresentativeName; }
    public Boolean getIsLegalRepresentative() { return isLegalRepresentative; }
    public void setIsLegalRepresentative(Boolean isLegalRepresentative) { this.isLegalRepresentative = isLegalRepresentative; }
}
```

- [ ] **Step 3: 创建 MasterAuthRequest**

```java
package com.example.hello.dto;

public class MasterAuthRequest {
    private String certificationUrl;
    private String representativeWorkUrl;

    public String getCertificationUrl() { return certificationUrl; }
    public void setCertificationUrl(String certificationUrl) { this.certificationUrl = certificationUrl; }
    public String getRepresentativeWorkUrl() { return representativeWorkUrl; }
    public void setRepresentativeWorkUrl(String representativeWorkUrl) { this.representativeWorkUrl = representativeWorkUrl; }
}
```

- [ ] **Step 4: 创建 CertificationResponse**

```java
package com.example.hello.dto;

import java.time.LocalDateTime;

import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;

public class CertificationResponse {
    private Long id;
    private CertificationType certificationType;
    private CertificationStatus status;
    private Boolean realNameVerified;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CertificationType getCertificationType() { return certificationType; }
    public void setCertificationType(CertificationType certificationType) { this.certificationType = certificationType; }
    public CertificationStatus getStatus() { return status; }
    public void setStatus(CertificationStatus status) { this.status = status; }
    public Boolean getRealNameVerified() { return realNameVerified; }
    public void setRealNameVerified(Boolean realNameVerified) { this.realNameVerified = realNameVerified; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 5: 创建 CertificationAuditRequest**

```java
package com.example.hello.dto;

import jakarta.validation.constraints.NotBlank;

public class CertificationAuditRequest {
    @NotBlank(message = "审核结果不能为空")
    private Boolean approved;

    private String rejectReason;

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
```

- [ ] **Step 6: 编译验证**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/example/hello/dto/RealNameAuthRequest.java \
        src/main/java/com/example/hello/dto/EnterpriseAuthRequest.java \
        src/main/java/com/example/hello/dto/MasterAuthRequest.java \
        src/main/java/com/example/hello/dto/CertificationResponse.java \
        src/main/java/com/example/hello/dto/CertificationAuditRequest.java
git commit -m "feat: 创建认证相关 DTO 类"
```

---

## Task 4: 修改 RegisterRequest 支持角色选择

**Files:**
- Modify: `src/main/java/com/example/hello/dto/RegisterRequest.java`

- [ ] **Step 1: 修改 RegisterRequest**

```java
package com.example.hello.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度至少6位")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    @NotBlank(message = "邀请码或剪艺码不能为空")
    @Pattern(regexp = "(?:\\d{6}|\\d{8})", message = "邀请码或剪艺码必须为6位或8位数字")
    private String invitationCode;

    private String roleType;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    public String getInvitationCode() { return invitationCode; }
    public void setInvitationCode(String invitationCode) { this.invitationCode = invitationCode; }
    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/example/hello/dto/RegisterRequest.java
git commit -m "feat: RegisterRequest 新增 roleType 字段支持角色选择"
```

---

## Task 5: 创建 UserPermissionService 权限检查服务

**Files:**
- Create: `src/main/java/com/example/hello/service/UserPermissionService.java`
- Create: `src/test/java/com/example/hello/service/UserPermissionServiceTest.java`

- [ ] **Step 1: 编写 UserPermissionService 测试**

```java
package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.entity.User;
import com.example.hello.entity.UserCertification;
import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserCertificationRepository;

@ExtendWith(MockitoExtension.class)
class UserPermissionServiceTest {

    @Mock
    private UserCertificationRepository certificationRepository;

    private UserPermissionService userPermissionService;

    @BeforeEach
    void setUp() {
        userPermissionService = new UserPermissionService(certificationRepository);
    }

    @Test
    void canSubmitPattern_guestCannotSubmit() {
        User user = new User("guest", "pass", UserRole.GUEST);
        assertFalse(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_superAdminCanSubmit() {
        User user = new User("admin", "pass", UserRole.SUPER_ADMIN);
        assertTrue(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_adminCanSubmit() {
        User user = new User("admin", "pass", UserRole.ADMIN);
        assertTrue(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_editorCanSubmit() {
        User user = new User("editor", "pass", UserRole.USER);
        assertTrue(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_regularUserNeedsRealNameAuth() {
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.empty());
        assertFalse(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_regularUserWithRealNameAuthCanSubmit() {
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        UserCertification cert = new UserCertification(user, CertificationType.REAL_NAME);
        cert.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(cert));
        assertTrue(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_enterpriseUserNeedsBothCerts() {
        User user = new User("enterprise", "pass", UserRole.ENTERPRISE_USER);
        UserCertification realNameCert = new UserCertification(user, CertificationType.REAL_NAME);
        realNameCert.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(realNameCert));
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.ENTERPRISE))
                .thenReturn(Optional.empty());
        assertFalse(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_enterpriseUserWithBothCertsCanSubmit() {
        User user = new User("enterprise", "pass", UserRole.ENTERPRISE_USER);
        UserCertification realNameCert = new UserCertification(user, CertificationType.REAL_NAME);
        realNameCert.setStatus(CertificationStatus.APPROVED);
        UserCertification enterpriseCert = new UserCertification(user, CertificationType.ENTERPRISE);
        enterpriseCert.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(realNameCert));
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.ENTERPRISE))
                .thenReturn(Optional.of(enterpriseCert));
        assertTrue(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_masterArtisanNeedsBothCerts() {
        User user = new User("master", "pass", UserRole.MASTER_ARTISAN);
        UserCertification realNameCert = new UserCertification(user, CertificationType.REAL_NAME);
        realNameCert.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(realNameCert));
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.MASTER))
                .thenReturn(Optional.empty());
        assertFalse(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_masterArtisanWithBothCertsCanSubmit() {
        User user = new User("master", "pass", UserRole.MASTER_ARTISAN);
        UserCertification realNameCert = new UserCertification(user, CertificationType.REAL_NAME);
        realNameCert.setStatus(CertificationStatus.APPROVED);
        UserCertification masterCert = new UserCertification(user, CertificationType.MASTER);
        masterCert.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(realNameCert));
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.MASTER))
                .thenReturn(Optional.of(masterCert));
        assertTrue(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void isRealNameVerified_returnsFalseWhenNoCert() {
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.empty());
        assertFalse(userPermissionService.isRealNameVerified(user));
    }

    @Test
    void isRealNameVerified_returnsFalseWhenPending() {
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        UserCertification cert = new UserCertification(user, CertificationType.REAL_NAME);
        cert.setStatus(CertificationStatus.PENDING);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(cert));
        assertFalse(userPermissionService.isRealNameVerified(user));
    }

    @Test
    void isRealNameVerified_returnsTrueWhenApproved() {
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        UserCertification cert = new UserCertification(user, CertificationType.REAL_NAME);
        cert.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(cert));
        assertTrue(userPermissionService.isRealNameVerified(user));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl . -Dtest=UserPermissionServiceTest -q`
Expected: FAIL (UserPermissionService 类不存在)

- [ ] **Step 3: 编写 UserPermissionService 实现**

```java
package com.example.hello.service;

import org.springframework.stereotype.Service;

import com.example.hello.entity.User;
import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserCertificationRepository;

@Service
public class UserPermissionService {

    private final UserCertificationRepository certificationRepository;

    public UserPermissionService(UserCertificationRepository certificationRepository) {
        this.certificationRepository = certificationRepository;
    }

    public boolean canSubmitPattern(User user) {
        if (user.getRole() == UserRole.GUEST) {
            return false;
        }
        if (user.getRole() == UserRole.SUPER_ADMIN
                || user.getRole() == UserRole.ADMIN
                || user.getRole() == UserRole.USER) {
            return true;
        }

        if (!isRealNameVerified(user)) {
            return false;
        }

        if (user.getRole() == UserRole.ENTERPRISE_USER) {
            return isRoleCertificationApproved(user, CertificationType.ENTERPRISE);
        }
        if (user.getRole() == UserRole.MASTER_ARTISAN) {
            return isRoleCertificationApproved(user, CertificationType.MASTER);
        }

        return true;
    }

    public boolean isRealNameVerified(User user) {
        return certificationRepository
                .findByUserAndCertificationType(user, CertificationType.REAL_NAME)
                .map(c -> c.getStatus() == CertificationStatus.APPROVED)
                .orElse(false);
    }

    public boolean isRoleCertificationApproved(User user, CertificationType type) {
        return certificationRepository
                .findByUserAndCertificationType(user, type)
                .map(c -> c.getStatus() == CertificationStatus.APPROVED)
                .orElse(false);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl . -Dtest=UserPermissionServiceTest -q`
Expected: Tests run: 12, Failures: 0

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/hello/service/UserPermissionService.java \
        src/test/java/com/example/hello/service/UserPermissionServiceTest.java
git commit -m "feat: 创建 UserPermissionService 权限检查服务及单元测试"
```

---

## Task 6: 创建 CertificationService 认证服务

**Files:**
- Create: `src/main/java/com/example/hello/service/CertificationService.java`
- Create: `src/test/java/com/example/hello/service/CertificationServiceTest.java`

- [ ] **Step 1: 编写 CertificationService 测试**

```java
package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.dto.EnterpriseAuthRequest;
import com.example.hello.dto.MasterAuthRequest;
import com.example.hello.dto.RealNameAuthRequest;
import com.example.hello.entity.User;
import com.example.hello.entity.UserCertification;
import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserCertificationRepository;
import com.example.hello.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CertificationServiceTest {

    @Mock
    private UserCertificationRepository certificationRepository;

    @Mock
    private UserRepository userRepository;

    private CertificationService certificationService;

    @BeforeEach
    void setUp() {
        certificationService = new CertificationService(certificationRepository, userRepository);
    }

    @Test
    void submitRealNameAuth_createsNewCertification() {
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        user.setId(1L);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.empty());
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RealNameAuthRequest request = new RealNameAuthRequest();
        request.setRealName("张三");
        request.setIdCardNumber("110101199001011234");
        request.setIdCardFrontUrl("https://example.com/front.jpg");
        request.setIdCardBackUrl("https://example.com/back.jpg");

        certificationService.submitRealNameAuth(user, request);

        ArgumentCaptor<UserCertification> captor = ArgumentCaptor.forClass(UserCertification.class);
        verify(certificationRepository).save(captor.capture());
        UserCertification saved = captor.getValue();
        assertEquals(CertificationType.REAL_NAME, saved.getCertificationType());
        assertEquals(CertificationStatus.PENDING, saved.getStatus());
        assertEquals("张三", saved.getRealName());
        assertEquals("110101199001011234", saved.getIdCardNumber());
    }

    @Test
    void submitRealNameAuth_throwsWhenAlreadyApproved() {
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        user.setId(1L);
        UserCertification existing = new UserCertification(user, CertificationType.REAL_NAME);
        existing.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(existing));

        RealNameAuthRequest request = new RealNameAuthRequest();
        request.setRealName("张三");
        request.setIdCardNumber("110101199001011234");
        request.setIdCardFrontUrl("https://example.com/front.jpg");
        request.setIdCardBackUrl("https://example.com/back.jpg");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> certificationService.submitRealNameAuth(user, request));
        assertEquals("实名认证已通过，无需重复提交", ex.getMessage());
        verify(certificationRepository, never()).save(any());
    }

    @Test
    void submitEnterpriseAuth_createsNewCertification() {
        User user = new User("enterprise", "pass", UserRole.ENTERPRISE_USER);
        user.setId(2L);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.ENTERPRISE))
                .thenReturn(Optional.empty());
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EnterpriseAuthRequest request = new EnterpriseAuthRequest();
        request.setBusinessLicenseUrl("https://example.com/license.jpg");
        request.setLegalRepresentativeName("李四");
        request.setIsLegalRepresentative(true);

        certificationService.submitEnterpriseAuth(user, request);

        ArgumentCaptor<UserCertification> captor = ArgumentCaptor.forClass(UserCertification.class);
        verify(certificationRepository).save(captor.capture());
        UserCertification saved = captor.getValue();
        assertEquals(CertificationType.ENTERPRISE, saved.getCertificationType());
        assertEquals("https://example.com/license.jpg", saved.getBusinessLicenseUrl());
    }

    @Test
    void submitEnterpriseAuth_requiresAuthorizationLetterWhenNotLegalRep() {
        User user = new User("enterprise", "pass", UserRole.ENTERPRISE_USER);
        user.setId(2L);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.ENTERPRISE))
                .thenReturn(Optional.empty());

        EnterpriseAuthRequest request = new EnterpriseAuthRequest();
        request.setBusinessLicenseUrl("https://example.com/license.jpg");
        request.setLegalRepresentativeName("李四");
        request.setIsLegalRepresentative(false);
        request.setAuthorizationLetterUrl(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> certificationService.submitEnterpriseAuth(user, request));
        assertEquals("非法人代表需上传授权书", ex.getMessage());
    }

    @Test
    void submitMasterAuth_createsNewCertification() {
        User user = new User("master", "pass", UserRole.MASTER_ARTISAN);
        user.setId(3L);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.MASTER))
                .thenReturn(Optional.empty());
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MasterAuthRequest request = new MasterAuthRequest();
        request.setCertificationUrl("https://example.com/cert.jpg");
        request.setRepresentativeWorkUrl("https://example.com/work.jpg");

        certificationService.submitMasterAuth(user, request);

        ArgumentCaptor<UserCertification> captor = ArgumentCaptor.forClass(UserCertification.class);
        verify(certificationRepository).save(captor.capture());
        UserCertification saved = captor.getValue();
        assertEquals(CertificationType.MASTER, saved.getCertificationType());
    }

    @Test
    void submitMasterAuth_requiresAtLeastOneMaterial() {
        User user = new User("master", "pass", UserRole.MASTER_ARTISAN);
        user.setId(3L);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.MASTER))
                .thenReturn(Optional.empty());

        MasterAuthRequest request = new MasterAuthRequest();
        request.setCertificationUrl(null);
        request.setRepresentativeWorkUrl(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> certificationService.submitMasterAuth(user, request));
        assertEquals("请至少上传认证证书或代表作", ex.getMessage());
    }

    @Test
    void approveCertification_updatesStatusAndAuditor() {
        UserCertification cert = new UserCertification(new User("user", "pass", UserRole.REGULAR_USER), CertificationType.REAL_NAME);
        cert.setId(1L);
        cert.setStatus(CertificationStatus.PENDING);
        when(certificationRepository.findById(1L)).thenReturn(Optional.of(cert));
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        certificationService.approveCertification(1L, 99L);

        assertEquals(CertificationStatus.APPROVED, cert.getStatus());
        assertEquals(99L, cert.getAuditorId());
        assertNotNull(cert.getAuditTime());
        verify(certificationRepository).save(cert);
    }

    @Test
    void rejectCertification_updatesStatusAndReason() {
        UserCertification cert = new UserCertification(new User("user", "pass", UserRole.REGULAR_USER), CertificationType.REAL_NAME);
        cert.setId(1L);
        cert.setStatus(CertificationStatus.PENDING);
        when(certificationRepository.findById(1L)).thenReturn(Optional.of(cert));
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        certificationService.rejectCertification(1L, 99L, "照片不清晰");

        assertEquals(CertificationStatus.REJECTED, cert.getStatus());
        assertEquals(99L, cert.getAuditorId());
        assertEquals("照片不清晰", cert.getRejectReason());
        assertNotNull(cert.getAuditTime());
        verify(certificationRepository).save(cert);
    }

    @Test
    void approveCertification_throwsWhenNotFound() {
        when(certificationRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> certificationService.approveCertification(999L, 1L));
        assertEquals("认证记录不存在", ex.getMessage());
    }

    @Test
    void approveCertification_throwsWhenAlreadyProcessed() {
        UserCertification cert = new UserCertification(new User("user", "pass", UserRole.REGULAR_USER), CertificationType.REAL_NAME);
        cert.setId(1L);
        cert.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findById(1L)).thenReturn(Optional.of(cert));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> certificationService.approveCertification(1L, 99L));
        assertEquals("该认证已审核完成", ex.getMessage());
        verify(certificationRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl . -Dtest=CertificationServiceTest -q`
Expected: FAIL (CertificationService 类不存在)

- [ ] **Step 3: 编写 CertificationService 实现**

```java
package com.example.hello.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.dto.CertificationResponse;
import com.example.hello.dto.EnterpriseAuthRequest;
import com.example.hello.dto.MasterAuthRequest;
import com.example.hello.dto.RealNameAuthRequest;
import com.example.hello.entity.User;
import com.example.hello.entity.UserCertification;
import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;
import com.example.hello.repository.UserCertificationRepository;
import com.example.hello.repository.UserRepository;

@Service
public class CertificationService {

    private final UserCertificationRepository certificationRepository;
    private final UserRepository userRepository;

    public CertificationService(UserCertificationRepository certificationRepository,
                                 UserRepository userRepository) {
        this.certificationRepository = certificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void submitRealNameAuth(User user, RealNameAuthRequest request) {
        UserCertification cert = certificationRepository
                .findByUserAndCertificationType(user, CertificationType.REAL_NAME)
                .orElse(null);

        if (cert != null && cert.getStatus() == CertificationStatus.APPROVED) {
            throw new RuntimeException("实名认证已通过，无需重复提交");
        }

        if (cert == null) {
            cert = new UserCertification(user, CertificationType.REAL_NAME);
        }

        cert.setRealName(request.getRealName());
        cert.setIdCardNumber(request.getIdCardNumber());
        cert.setIdCardFrontUrl(request.getIdCardFrontUrl());
        cert.setIdCardBackUrl(request.getIdCardBackUrl());
        cert.setStatus(CertificationStatus.PENDING);
        cert.setRejectReason(null);
        cert.setAuditorId(null);
        cert.setAuditTime(null);

        certificationRepository.save(cert);
    }

    @Transactional
    public void submitEnterpriseAuth(User user, EnterpriseAuthRequest request) {
        if (Boolean.FALSE.equals(request.getIsLegalRepresentative())
                && (request.getAuthorizationLetterUrl() == null
                    || request.getAuthorizationLetterUrl().isBlank())) {
            throw new RuntimeException("非法人代表需上传授权书");
        }

        UserCertification cert = certificationRepository
                .findByUserAndCertificationType(user, CertificationType.ENTERPRISE)
                .orElse(null);

        if (cert != null && cert.getStatus() == CertificationStatus.APPROVED) {
            throw new RuntimeException("企业认证已通过，无需重复提交");
        }

        if (cert == null) {
            cert = new UserCertification(user, CertificationType.ENTERPRISE);
        }

        cert.setBusinessLicenseUrl(request.getBusinessLicenseUrl());
        cert.setAuthorizationLetterUrl(request.getAuthorizationLetterUrl());
        cert.setLegalRepresentativeName(request.getLegalRepresentativeName());
        cert.setIsLegalRepresentative(request.getIsLegalRepresentative());
        cert.setStatus(CertificationStatus.PENDING);
        cert.setRejectReason(null);
        cert.setAuditorId(null);
        cert.setAuditTime(null);

        certificationRepository.save(cert);
    }

    @Transactional
    public void submitMasterAuth(User user, MasterAuthRequest request) {
        if ((request.getCertificationUrl() == null || request.getCertificationUrl().isBlank())
                && (request.getRepresentativeWorkUrl() == null || request.getRepresentativeWorkUrl().isBlank())) {
            throw new RuntimeException("请至少上传认证证书或代表作");
        }

        UserCertification cert = certificationRepository
                .findByUserAndCertificationType(user, CertificationType.MASTER)
                .orElse(null);

        if (cert != null && cert.getStatus() == CertificationStatus.APPROVED) {
            throw new RuntimeException("技艺认证已通过，无需重复提交");
        }

        if (cert == null) {
            cert = new UserCertification(user, CertificationType.MASTER);
        }

        cert.setCertificationUrl(request.getCertificationUrl());
        cert.setRepresentativeWorkUrl(request.getRepresentativeWorkUrl());
        cert.setStatus(CertificationStatus.PENDING);
        cert.setRejectReason(null);
        cert.setAuditorId(null);
        cert.setAuditTime(null);

        certificationRepository.save(cert);
    }

    @Transactional
    public void approveCertification(Long certificationId, Long auditorId) {
        UserCertification cert = certificationRepository.findById(certificationId)
                .orElseThrow(() -> new RuntimeException("认证记录不存在"));

        if (cert.getStatus() != CertificationStatus.PENDING) {
            throw new RuntimeException("该认证已审核完成");
        }

        cert.setStatus(CertificationStatus.APPROVED);
        cert.setAuditorId(auditorId);
        cert.setAuditTime(LocalDateTime.now());

        if (cert.getCertificationType() == CertificationType.REAL_NAME) {
            cert.setRealNameVerified(true);
        }

        certificationRepository.save(cert);
    }

    @Transactional
    public void rejectCertification(Long certificationId, Long auditorId, String rejectReason) {
        UserCertification cert = certificationRepository.findById(certificationId)
                .orElseThrow(() -> new RuntimeException("认证记录不存在"));

        if (cert.getStatus() != CertificationStatus.PENDING) {
            throw new RuntimeException("该认证已审核完成");
        }

        cert.setStatus(CertificationStatus.REJECTED);
        cert.setAuditorId(auditorId);
        cert.setAuditTime(LocalDateTime.now());
        cert.setRejectReason(rejectReason);

        certificationRepository.save(cert);
    }

    public List<CertificationResponse> getMyCertifications(User user) {
        return certificationRepository.findByUser(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<CertificationResponse> getPendingCertifications() {
        return certificationRepository.findByStatus(CertificationStatus.PENDING).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private CertificationResponse toResponse(UserCertification cert) {
        CertificationResponse response = new CertificationResponse();
        response.setId(cert.getId());
        response.setCertificationType(cert.getCertificationType());
        response.setStatus(cert.getStatus());
        response.setRealNameVerified(cert.getRealNameVerified());
        response.setRejectReason(cert.getRejectReason());
        response.setCreatedAt(cert.getCreatedAt());
        response.setUpdatedAt(cert.getUpdatedAt());
        return response;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl . -Dtest=CertificationServiceTest -q`
Expected: Tests run: 10, Failures: 0

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/hello/service/CertificationService.java \
        src/test/java/com/example/hello/service/CertificationServiceTest.java
git commit -m "feat: 创建 CertificationService 认证服务及单元测试"
```

---

## Task 7: 创建 CertificationController 认证控制器

**Files:**
- Create: `src/main/java/com/example/hello/controller/CertificationController.java`
- Create: `src/test/java/com/example/hello/controller/CertificationControllerTest.java`

- [ ] **Step 1: 编写 CertificationController 测试**

```java
package com.example.hello.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.hello.dto.CertificationResponse;
import com.example.hello.dto.EnterpriseAuthRequest;
import com.example.hello.dto.MasterAuthRequest;
import com.example.hello.dto.RealNameAuthRequest;
import com.example.hello.entity.User;
import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserRepository;
import com.example.hello.service.CertificationService;
import com.example.hello.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(CertificationController.class)
class CertificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CertificationService certificationService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void submitRealNameAuth_returnsSuccess() throws Exception {
        when(jwtUtil.extractUserId("test-token")).thenReturn(1L);
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        doNothing().when(certificationService).submitRealNameAuth(any(), any());

        RealNameAuthRequest request = new RealNameAuthRequest();
        request.setRealName("张三");
        request.setIdCardNumber("110101199001011234");
        request.setIdCardFrontUrl("https://example.com/front.jpg");
        request.setIdCardBackUrl("https://example.com/back.jpg");

        mockMvc.perform(post("/api/certifications/real-name")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("实名认证提交成功"));
    }

    @Test
    void submitRealNameAuth_returns401WhenNoToken() throws Exception {
        RealNameAuthRequest request = new RealNameAuthRequest();
        request.setRealName("张三");
        request.setIdCardNumber("110101199001011234");
        request.setIdCardFrontUrl("https://example.com/front.jpg");
        request.setIdCardBackUrl("https://example.com/back.jpg");

        mockMvc.perform(post("/api/certifications/real-name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyCertifications_returnsList() throws Exception {
        when(jwtUtil.extractUserId("test-token")).thenReturn(1L);
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));

        CertificationResponse response = new CertificationResponse();
        response.setId(1L);
        response.setCertificationType(CertificationType.REAL_NAME);
        response.setStatus(CertificationStatus.PENDING);
        when(certificationService.getMyCertifications(user)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/certifications/my")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].certificationType").value("REAL_NAME"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getPendingCertifications_returns403ForNonAdmin() throws Exception {
        when(jwtUtil.extractUserId("test-token")).thenReturn(1L);
        when(jwtUtil.extractRole("test-token")).thenReturn("REGULAR_USER");
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));

        mockMvc.perform(get("/api/certifications/pending")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveCertification_returnsSuccessForAdmin() throws Exception {
        when(jwtUtil.extractUserId("admin-token")).thenReturn(99L);
        when(jwtUtil.extractRole("admin-token")).thenReturn("ADMIN");
        User admin = new User("admin", "pass", UserRole.ADMIN);
        admin.setId(99L);
        when(userRepository.findById(99L)).thenReturn(java.util.Optional.of(admin));
        doNothing().when(certificationService).approveCertification(1L, 99L);

        mockMvc.perform(post("/api/certifications/1/approve")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("审核通过"));
    }

    @Test
    void rejectCertification_returnsSuccessForAdmin() throws Exception {
        when(jwtUtil.extractUserId("admin-token")).thenReturn(99L);
        when(jwtUtil.extractRole("admin-token")).thenReturn("ADMIN");
        User admin = new User("admin", "pass", UserRole.ADMIN);
        admin.setId(99L);
        when(userRepository.findById(99L)).thenReturn(java.util.Optional.of(admin));
        doNothing().when(certificationService).rejectCertification(eq(1L), eq(99L), any());

        String body = "{\"approved\": false, \"rejectReason\": \"照片不清晰\"}";

        mockMvc.perform(post("/api/certifications/1/reject")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("审核拒绝"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl . -Dtest=CertificationControllerTest -q`
Expected: FAIL (CertificationController 类不存在)

- [ ] **Step 3: 编写 CertificationController 实现**

```java
package com.example.hello.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.CertificationAuditRequest;
import com.example.hello.dto.CertificationResponse;
import com.example.hello.dto.EnterpriseAuthRequest;
import com.example.hello.dto.MasterAuthRequest;
import com.example.hello.dto.RealNameAuthRequest;
import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserRepository;
import com.example.hello.service.CertificationService;
import com.example.hello.util.JwtUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/certifications")
public class CertificationController {

    private final CertificationService certificationService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public CertificationController(CertificationService certificationService,
                                    JwtUtil jwtUtil,
                                    UserRepository userRepository) {
        this.certificationService = certificationService;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @PostMapping("/real-name")
    public ResponseEntity<?> submitRealNameAuth(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody RealNameAuthRequest request) {
        try {
            if (token == null) {
                return ResponseEntity.status(401).body(Map.of("message", "未登录"));
            }
            User user = getUserFromToken(token);
            certificationService.submitRealNameAuth(user, request);
            return ResponseEntity.ok(Map.of("message", "实名认证提交成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/enterprise")
    public ResponseEntity<?> submitEnterpriseAuth(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody EnterpriseAuthRequest request) {
        try {
            if (token == null) {
                return ResponseEntity.status(401).body(Map.of("message", "未登录"));
            }
            User user = getUserFromToken(token);
            if (user.getRole() != UserRole.ENTERPRISE_USER) {
                return ResponseEntity.status(403).body(Map.of("message", "仅企商用户可提交企业认证"));
            }
            certificationService.submitEnterpriseAuth(user, request);
            return ResponseEntity.ok(Map.of("message", "企业认证提交成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/master")
    public ResponseEntity<?> submitMasterAuth(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody MasterAuthRequest request) {
        try {
            if (token == null) {
                return ResponseEntity.status(401).body(Map.of("message", "未登录"));
            }
            User user = getUserFromToken(token);
            if (user.getRole() != UserRole.MASTER_ARTISAN) {
                return ResponseEntity.status(403).body(Map.of("message", "仅技艺大师可提交技艺认证"));
            }
            certificationService.submitMasterAuth(user, request);
            return ResponseEntity.ok(Map.of("message", "技艺认证提交成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyCertifications(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null) {
                return ResponseEntity.status(401).body(Map.of("message", "未登录"));
            }
            User user = getUserFromToken(token);
            List<CertificationResponse> certifications = certificationService.getMyCertifications(user);
            return ResponseEntity.ok(certifications);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingCertifications(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null) {
                return ResponseEntity.status(401).body(Map.of("message", "未登录"));
            }
            String role = getRoleFromToken(token);
            if (!"SUPER_ADMIN".equals(role) && !"ADMIN".equals(role)) {
                return ResponseEntity.status(403).body(Map.of("message", "无权限查看待审核列表"));
            }
            List<CertificationResponse> certifications = certificationService.getPendingCertifications();
            return ResponseEntity.ok(certifications);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveCertification(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        try {
            if (token == null) {
                return ResponseEntity.status(401).body(Map.of("message", "未登录"));
            }
            String role = getRoleFromToken(token);
            if (!"SUPER_ADMIN".equals(role) && !"ADMIN".equals(role)) {
                return ResponseEntity.status(403).body(Map.of("message", "无权限审核认证"));
            }
            Long auditorId = getUserIdFromToken(token);
            certificationService.approveCertification(id, auditorId);
            return ResponseEntity.ok(Map.of("message", "审核通过"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectCertification(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id,
            @RequestBody CertificationAuditRequest request) {
        try {
            if (token == null) {
                return ResponseEntity.status(401).body(Map.of("message", "未登录"));
            }
            String role = getRoleFromToken(token);
            if (!"SUPER_ADMIN".equals(role) && !"ADMIN".equals(role)) {
                return ResponseEntity.status(403).body(Map.of("message", "无权限审核认证"));
            }
            Long auditorId = getUserIdFromToken(token);
            certificationService.rejectCertification(id, auditorId, request.getRejectReason());
            return ResponseEntity.ok(Map.of("message", "审核拒绝"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }

    private String getRoleFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractRole(jwt);
    }

    private User getUserFromToken(String token) {
        Long userId = getUserIdFromToken(token);
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl . -Dtest=CertificationControllerTest -q`
Expected: Tests run: 6, Failures: 0

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/hello/controller/CertificationController.java \
        src/test/java/com/example/hello/controller/CertificationControllerTest.java
git commit -m "feat: 创建 CertificationController 认证控制器及集成测试"
```

---

## Task 8: 改造 AuthService 注册流程

**Files:**
- Modify: `src/main/java/com/example/hello/service/AuthService.java`
- Modify: `src/test/java/com/example/hello/service/AuthServiceTest.java`

- [ ] **Step 1: 修改 AuthService.register() 方法**

在 `AuthService.java` 中修改 `register` 方法，支持角色选择：

```java
@Transactional
public AuthResponse register(RegisterRequest request) {
    if (!request.getPassword().equals(request.getConfirmPassword())) {
        throw new RuntimeException("两次密码不一致");
    }
    if (userRepository.existsByUsername(request.getUsername())) {
        throw new RuntimeException("用户名已存在");
    }

    InvitationCodeService.CodeConsumeResult consumeResult = invitationCodeService.consumeCode(request.getInvitationCode());

    User user = new User(request.getUsername(), passwordEncoder.encode(request.getPassword()));

    // 根据 roleType 设置角色
    String roleType = request.getRoleType();
    if (roleType != null && !roleType.isBlank()) {
        try {
            UserRole selectedRole = UserRole.valueOf(roleType);
            // 只允许选择普通用户、企商用户、技艺大师
            if (selectedRole == UserRole.REGULAR_USER
                    || selectedRole == UserRole.ENTERPRISE_USER
                    || selectedRole == UserRole.MASTER_ARTISAN) {
                user.setRole(selectedRole);
            } else {
                throw new RuntimeException("不支持的角色类型");
            }
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("无效的角色类型");
        }
    } else {
        // 没有指定角色时，默认为录入员（兼容旧逻辑）
        user.setRole(UserRole.USER);
    }

    userRepository.save(user);

    if (consumeResult != null && consumeResult.source() == InvitationCodeService.CodeSource.APP) {
        appRegistrationCallbackService.notifyRegisterSuccess(
                request.getInvitationCode(),
                String.valueOf(user.getId()),
                user.getUsername(),
                null);
    }
    return new AuthResponse(null, "注册成功");
}
```

- [ ] **Step 2: 更新 AuthServiceTest 测试用例**

在 `AuthServiceTest.java` 中添加新测试：

```java
@Test
void register_shouldSetRegularUserRoleWhenRoleTypeProvided() {
    RegisterRequest request = buildRegisterRequest();
    request.setRoleType("REGULAR_USER");
    when(userRepository.existsByUsername("alice")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
    when(invitationCodeService.consumeCode("123456")).thenReturn(InvitationCodeService.CodeConsumeResult.local());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AuthResponse response = authService.register(request);

    assertNull(response.getToken());
    assertEquals("注册成功", response.getMessage());

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User savedUser = userCaptor.getValue();
    assertEquals(UserRole.REGULAR_USER, savedUser.getRole());
}

@Test
void register_shouldSetEnterpriseUserRoleWhenRoleTypeProvided() {
    RegisterRequest request = buildRegisterRequest();
    request.setRoleType("ENTERPRISE_USER");
    when(userRepository.existsByUsername("alice")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
    when(invitationCodeService.consumeCode("123456")).thenReturn(InvitationCodeService.CodeConsumeResult.app());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
        User saved = invocation.getArgument(0);
        saved.setId(10L);
        return saved;
    });

    AuthResponse response = authService.register(request);

    assertEquals("注册成功", response.getMessage());

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User savedUser = userCaptor.getValue();
    assertEquals(UserRole.ENTERPRISE_USER, savedUser.getRole());
}

@Test
void register_shouldRejectInvalidRoleType() {
    RegisterRequest request = buildRegisterRequest();
    request.setRoleType("INVALID_ROLE");
    when(userRepository.existsByUsername("alice")).thenReturn(false);

    RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));
    assertEquals("无效的角色类型", ex.getMessage());
    verify(userRepository, never()).save(any());
}

@Test
void register_shouldRejectAdminRoleType() {
    RegisterRequest request = buildRegisterRequest();
    request.setRoleType("ADMIN");
    when(userRepository.existsByUsername("alice")).thenReturn(false);

    RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));
    assertEquals("不支持的角色类型", ex.getMessage());
    verify(userRepository, never()).save(any());
}

@Test
void register_shouldDefaultToUserRoleWhenNoRoleType() {
    RegisterRequest request = buildRegisterRequest();
    request.setRoleType(null);
    when(userRepository.existsByUsername("alice")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
    when(invitationCodeService.consumeCode("123456")).thenReturn(InvitationCodeService.CodeConsumeResult.local());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    authService.register(request);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User savedUser = userCaptor.getValue();
    assertEquals(UserRole.USER, savedUser.getRole());
}
```

- [ ] **Step 3: 运行测试验证通过**

Run: `mvn test -pl . -Dtest=AuthServiceTest -q`
Expected: Tests run: 10, Failures: 0

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/example/hello/service/AuthService.java \
        src/test/java/com/example/hello/service/AuthServiceTest.java
git commit -m "feat: 改造 AuthService 注册流程支持角色选择"
```

---

## Task 9: 全量测试验证

**Files:**
- (无新增/修改文件)

- [ ] **Step 1: 运行全量测试**

Run: `mvn test -pl . -q`
Expected: All tests pass

- [ ] **Step 2: 运行编译检查**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 最终提交（如需要）**

如果有任何修复，提交修复：

```bash
git add -A
git commit -m "fix: 修复测试和编译问题"
```

---

## 自检清单

### Spec 覆盖检查

| Spec 需求 | 对应 Task |
|-----------|----------|
| 扩展 UserRole 枚举 | Task 1 |
| 新增 CertificationType 枚举 | Task 1 |
| 新增 CertificationStatus 枚举 | Task 1 |
| 创建 UserCertification 实体 | Task 2 |
| 创建 UserCertificationRepository | Task 2 |
| 创建认证相关 DTO | Task 3 |
| 修改 RegisterRequest | Task 4 |
| 创建 UserPermissionService | Task 5 |
| 创建 CertificationService | Task 6 |
| 创建 CertificationController | Task 7 |
| 改造 AuthService 注册流程 | Task 8 |
| 实名认证提交 API | Task 7 |
| 企业认证提交 API | Task 7 |
| 技艺认证提交 API | Task 7 |
| 获取我的认证状态 API | Task 7 |
| 获取待审核列表 API | Task 7 |
| 审核通过/拒绝 API | Task 7 |
| 权限检查逻辑 | Task 5 |
| 注册时角色选择 | Task 8 |
| 邀请码验证（复用现有） | Task 8 |

### 未覆盖项（前端相关，不在后端实施计划范围内）

- 前端强制实名认证弹窗
- 前端认证状态检查
- 前端认证材料上传界面
