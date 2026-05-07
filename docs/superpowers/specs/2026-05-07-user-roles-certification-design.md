# 用户角色扩展与认证系统设计文档

> 创建时间：2026-05-07
> 状态：设计完成，待审核

## 1. 概述

### 1.1 需求背景

当前系统支持四种用户角色：超级管理员、管理员、录入员、游客。为了支持更多类型的用户（如企业用户、技艺大师），需要扩展角色体系并引入认证机制。

### 1.2 新增角色

| 角色 | 代码 | 名称 | 认证要求 | 权限 |
|------|------|------|---------|------|
| REGULAR_USER | REGULAR_USER | 普通用户 | 实名认证 | 浏览、互动、提交纹样 |
| ENTERPRISE_USER | ENTERPRISE_USER | 企商用户 | 实名认证 + 企业认证 | 浏览、互动、提交纹样 |
| MASTER_ARTISAN | MASTER_ARTISAN | 技艺大师 | 实名认证 + 技艺认证 | 浏览、互动、提交纹样 |

### 1.3 现有角色调整

| 角色 | 调整说明 |
|------|---------|
| 超级管理员 | 保持不变 |
| 管理员 | 保持不变，新增认证审核权限 |
| 录入员 | 保持不变，注册时需要纹样系统邀请码 |
| 游客 | 保持不变 |

## 2. 数据模型

### 2.1 扩展 UserRole 枚举

```java
public enum UserRole {
    SUPER_ADMIN("超级管理员"),
    ADMIN("管理员"),
    USER("录入员"),
    GUEST("游客"),
    REGULAR_USER("普通用户"),
    ENTERPRISE_USER("企商用户"),
    MASTER_ARTISAN("技艺大师");

    private final String name;
    // constructor, getter
}
```

### 2.2 新增枚举

```java
public enum CertificationType {
    REAL_NAME("实名认证"),
    ENTERPRISE("企业认证"),
    MASTER("技艺认证");

    private final String name;
    // constructor, getter
}

public enum CertificationStatus {
    PENDING("待审核"),
    APPROVED("已通过"),
    REJECTED("已拒绝");

    private final String name;
    // constructor, getter
}
```

### 2.3 UserCertification 实体

```java
@Entity
@Table(name = "user_certifications")
public class UserCertification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificationType certificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificationStatus status = CertificationStatus.PENDING;

    // 实名认证字段
    private String realName;
    private String idCardNumber;
    private String idCardFrontUrl;
    private String idCardBackUrl;
    private Boolean realNameVerified = false;

    // 企业认证字段
    private String businessLicenseUrl;
    private String authorizationLetterUrl;
    private String legalRepresentativeName;
    private Boolean isLegalRepresentative;

    // 技艺认证字段
    private String certificationUrl;
    private String representativeWorkUrl;

    // 审核信息
    private String rejectReason;
    private Long auditorId;
    private LocalDateTime auditTime;

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
}
```

### 2.4 数据库表结构

**user_certifications 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户ID（外键，唯一） |
| certification_type | VARCHAR(20) | 认证类型 |
| status | VARCHAR(20) | 状态 |
| real_name | VARCHAR(50) | 真实姓名 |
| id_card_number | VARCHAR(18) | 身份证号 |
| id_card_front_url | VARCHAR(500) | 身份证正面照片 URL |
| id_card_back_url | VARCHAR(500) | 身份证背面照片 URL |
| real_name_verified | BOOLEAN | 实名认证是否通过 |
| business_license_url | VARCHAR(500) | 营业执照图片 URL |
| authorization_letter_url | VARCHAR(500) | 授权书图片 URL |
| legal_representative_name | VARCHAR(50) | 法人姓名 |
| is_legal_representative | BOOLEAN | 是否为法人 |
| certification_url | VARCHAR(500) | 官方机构认证证书 URL |
| representative_work_url | VARCHAR(500) | 代表作 URL |
| reject_reason | VARCHAR(500) | 拒绝原因 |
| auditor_id | BIGINT | 审核人ID |
| audit_time | DATETIME | 审核时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

## 3. 认证流程

### 3.1 完整用户旅程

```
注册（选角色）→ 首次登录 → 强制实名认证弹窗（身份证正反面）
                                    ↓
                            实名认证通过 → 普通用户获得完整功能
                                    ↓
                    企商用户/技艺大师 → 提交角色认证材料
                                    ↓
                            角色认证通过 → 获得完整功能
```

### 3.2 注册流程

**普通用户/企商用户/技艺大师**：
```
输入用户名/密码/邀请码 → 选择角色类型 → 创建用户 → 返回成功
```

**录入员**：
```
输入用户名/密码/纹样系统邀请码 → 验证邀请码 → 创建用户（角色=录入员）→ 返回成功
```

### 3.3 实名认证流程

1. 用户登录后，前端检查认证状态
2. 如果未实名认证，显示强制弹窗
3. 用户上传身份证正反面照片
4. 提交后状态为 PENDING
5. 管理员审核通过后，更新 realNameVerified = true

### 3.4 角色认证流程

**企商用户**：
1. 上传营业执照
2. 如果不是法人，还需要上传授权书
3. 填写法人姓名
4. 提交后状态为 PENDING
5. 管理员审核

**技艺大师**：
1. 上传官方机构认证证书或代表作
2. 提交后状态为 PENDING
3. 管理员审核

### 3.5 重新提交流程

认证被拒绝后：
1. 用户查看拒绝原因
2. 重新上传认证材料
3. 提交后状态更新为 PENDING
4. 管理员重新审核

## 4. 权限控制

### 4.1 权限矩阵

| 功能 | 超级管理员 | 管理员 | 录入员 | 普通用户 | 企商用户 | 技艺大师 | 游客 |
|------|-----------|--------|--------|---------|---------|---------|------|
| 浏览纹样 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 提交纹样 | ✅ | ✅ | ✅ | ✅* | ✅* | ✅* | ❌ |
| 保存草稿 | ✅ | ✅ | ✅ | ✅* | ✅* | ✅* | ❌ |
| 互动功能 | ✅ | ✅ | ✅ | ✅* | ✅* | ✅* | ❌ |
| 审核纹样 | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| 用户管理 | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| 认证审核 | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

**说明**：`*` 表示需要完成实名认证后才能使用

### 4.2 认证状态与功能关系

| 用户类型 | 认证要求 | 未认证时 | 认证后 |
|---------|---------|---------|--------|
| 普通用户 | 实名认证 | 仅可浏览 | 完整功能 |
| 企商用户 | 实名认证 + 企业认证 | 仅可浏览 | 完整功能 |
| 技艺大师 | 实名认证 + 技艺认证 | 仅可浏览 | 完整功能 |
| 录入员 | 邀请码 | N/A（注册即完整） | N/A |
| 游客 | 无 | 仅可浏览 | N/A |
| 管理员 | 无 | 完整功能 | N/A |
| 超级管理员 | 无 | 完整功能 | N/A |

### 4.3 权限检查服务

```java
@Service
public class UserPermissionService {

    private final UserCertificationRepository certificationRepository;

    public boolean canSubmitPattern(User user) {
        if (user.getRole() == UserRole.GUEST) return false;
        if (user.getRole() == UserRole.SUPER_ADMIN ||
            user.getRole() == UserRole.ADMIN ||
            user.getRole() == UserRole.USER) return true;

        if (!isRealNameVerified(user)) return false;

        if (user.getRole() == UserRole.ENTERPRISE_USER ||
            user.getRole() == UserRole.MASTER_ARTISAN) {
            return isRoleCertificationApproved(user);
        }

        return true;
    }

    public boolean isRealNameVerified(User user) {
        return certificationRepository
            .findByUserAndCertificationType(user, CertificationType.REAL_NAME)
            .map(c -> c.getStatus() == CertificationStatus.APPROVED)
            .orElse(false);
    }

    public boolean isRoleCertificationApproved(User user) {
        CertificationType type = user.getRole() == UserRole.ENTERPRISE_USER ?
            CertificationType.ENTERPRISE : CertificationType.MASTER;
        return certificationRepository
            .findByUserAndCertificationType(user, type)
            .map(c -> c.getStatus() == CertificationStatus.APPROVED)
            .orElse(false);
    }
}
```

## 5. API 设计

### 5.1 认证相关 API

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/certifications/real-name` | 提交实名认证 | 已登录用户 |
| POST | `/api/certifications/enterprise` | 提交企业认证 | 企商用户 |
| POST | `/api/certifications/master` | 提交技艺认证 | 技艺大师 |
| GET | `/api/certifications/my` | 获取我的认证状态 | 已登录用户 |
| PUT | `/api/certifications/my` | 重新提交认证材料 | 已登录用户 |
| GET | `/api/certifications/pending` | 获取待审核认证列表 | 管理员 |
| POST | `/api/certifications/{id}/approve` | 审核通过认证 | 管理员 |
| POST | `/api/certifications/{id}/reject` | 审核拒绝认证 | 管理员 |

### 5.2 注册 API 修改

**请求体扩展**：

```json
{
    "username": "string",
    "password": "string",
    "confirmPassword": "string",
    "invitationCode": "string",
    "roleType": "REGULAR_USER | ENTERPRISE_USER | MASTER_ARTISAN"
}
```

**说明**：
- `roleType` 为可选字段，默认为 `REGULAR_USER`
- 录入员注册时不需要 `roleType`，通过邀请码自动设置角色

### 5.3 响应码设计

| 场景 | HTTP 状态码 | 响应码 | 说明 |
|------|-----------|--------|------|
| 未实名认证访问受限功能 | 403 | NEED_REAL_NAME_AUTH | 需要实名认证 |
| 未角色认证访问受限功能 | 403 | NEED_ROLE_CERTIFICATION | 需要角色认证 |
| 认证审核中 | 200 | CERTIFICATION_PENDING | 认证正在审核中 |
| 认证被拒绝 | 200 | CERTIFICATION_REJECTED | 认证被拒绝，可重新提交 |

## 6. 实现要点

### 6.1 文件上传

复用现有 `ImageService`，认证材料存储在 S3 的 `certifications/` 目录下：

```
certifications/
├── real-name/
│   ├── {userId}_front.jpg
│   └── {userId}_back.jpg
├── enterprise/
│   ├── {userId}_license.jpg
│   └── {userId}_authorization.jpg
└── master/
    ├── {userId}_certification.jpg
    └── {userId}_work.jpg
```

### 6.2 注册流程改造

修改 `AuthService.register()` 方法：
1. 根据 `roleType` 设置用户角色
2. 如果是企商用户或技艺大师，创建认证记录（状态为 PENDING）
3. 普通用户不创建认证记录（登录后强制实名认证）

### 6.3 认证审核流程

新增 `CertificationService`：
1. `submitRealNameAuth()` - 提交实名认证
2. `submitEnterpriseAuth()` - 提交企业认证
3. `submitMasterAuth()` - 提交技艺认证
4. `approveCertification()` - 审核通过
5. `rejectCertification()` - 审核拒绝
6. `getMyCertifications()` - 获取用户认证状态
7. `getPendingCertifications()` - 获取待审核列表

### 6.4 前端强制弹窗

前端需要在每次页面加载时检查认证状态：
1. 调用 `GET /api/certifications/my`
2. 如果 `realNameVerified` 为 `false`，显示强制实名认证弹窗
3. 弹窗中提供身份证上传表单
4. 提交后显示"审核中"状态

## 7. 测试策略

### 7.1 单元测试

- `UserPermissionService` - 权限检查逻辑
- `CertificationService` - 认证提交和审核逻辑
- `AuthService` - 注册流程改造

### 7.2 集成测试

- 注册流程（不同角色类型）
- 认证提交流程
- 认证审核流程
- 权限检查 API

### 7.3 测试用例

1. 普通用户注册后，未实名认证时只能浏览
2. 普通用户实名认证后，可以提交纹样
3. 企商用户需要实名认证 + 企业认证才能提交纹样
4. 技艺大师需要实名认证 + 技艺认证才能提交纹样
5. 录入员注册时需要有效的邀请码
6. 认证被拒绝后可以重新提交
7. 管理员可以审核认证申请

## 8. 风险与注意事项

### 8.1 数据安全

- 身份证号需要加密存储
- 认证材料 URL 需要访问控制
- 审核日志需要记录

### 8.2 性能考虑

- 认证状态检查可能频繁调用，考虑缓存
- 文件上传大小限制需要配置

### 8.3 用户体验

- 实名认证弹窗需要友好提示
- 认证状态需要实时反馈
- 拒绝原因需要清晰说明

## 9. 未来扩展

### 9.1 可能的扩展点

1. 支持更多认证类型（如行业协会认证）
2. 自动化认证审核（OCR 识别营业执照）
3. 认证等级体系（不同认证等级不同权限）
4. 认证有效期管理

### 9.2 扩展建议

- 保持 `CertificationType` 枚举的可扩展性
- 认证材料字段使用 JSON 或分类存储，便于扩展
- 权限检查逻辑使用策略模式，便于添加新规则
