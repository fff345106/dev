# 前端适配指南：认证状态弹窗控制

> 后端已部署，`User` 实体新增 `certificationStatus` 字段

---

## 变更概要

后端 `GET /api/users/{userId}` 响应新增字段：

```json
{
  "id": 13,
  "username": "master_user",
  "role": "MASTER_ARTISAN",
  "avatarUrl": "...",
  "certificationStatus": "PENDING",   // <-- 新增字段
  "createdAt": "2026-05-10T10:00:00"
}
```

### `certificationStatus` 取值

| 值 | 含义 | 前端应如何处理 |
|---|---|---|
| `null` | 未提交过认证 | 显示认证弹窗，引导用户提交 |
| `"PENDING"` | 已提交，等待审核 | **不弹窗**，显示"认证审核中"状态 |
| `"APPROVED"` | 已通过 | **不弹窗**，正常使用全部功能 |
| `"REJECTED"` | 被拒绝 | 显示弹窗，提示被拒原因，允许重新提交 |

---

## 前端修改点

### 1. 认证弹窗显示逻辑

**修改前**（当前逻辑，可能类似）：

```javascript
// 可能是检查 realNameVerified 或者直接弹窗
if (!user.realNameVerified) {
  showCertificationModal()
}
```

**修改后**：

```javascript
// 基于 certificationStatus 判断
function shouldShowCertificationModal(user) {
  // null = 未提交，REJECTED = 被拒绝可重新提交
  return user.certificationStatus === null || user.certificationStatus === 'REJECTED'
}
```

### 2. 用户信息获取

在用户登录后或进入页面时，调用 `GET /api/users/{userId}` 获取用户信息，响应中已包含 `certificationStatus`：

```javascript
const response = await fetch(`/api/users/${userId}`, {
  headers: { 'Authorization': `Bearer ${token}` }
})
const user = await response.json()
// user.certificationStatus 即可使用
```

### 3. 认证状态展示（可选）

在用户个人中心或设置页面，展示当前认证状态：

```javascript
function getCertificationStatusText(status) {
  switch (status) {
    case null:          return '未认证'
    case 'PENDING':     return '认证审核中'
    case 'APPROVED':    return '已认证'
    case 'REJECTED':    return '认证被拒绝，点击重新提交'
    default:            return '未认证'
  }
}

function getCertificationStatusColor(status) {
  switch (status) {
    case 'PENDING':     return 'orange'   // 橙色 - 等待中
    case 'APPROVED':    return 'green'    // 绿色 - 已通过
    case 'REJECTED':    return 'red'      // 红色 - 被拒绝
    default:            return 'gray'     // 灰色 - 未认证
  }
}
```

### 4. 各角色弹窗规则

**所有注册用户**首次登录都需要完成实名认证，不只是技艺大师。

| 角色 | 需要实名认证 | 额外要求 |
|------|:---:|------|
| `REGULAR_USER`（普通用户） | 是 | 无 |
| `ENTERPRISE_USER`（企商用户） | 是 | 还需企业认证 |
| `MASTER_ARTISAN`（技艺大师） | 是 | 还需技艺认证 |
| `GUEST`（游客） | 否 | 游客无需认证 |

```javascript
function shouldShowCertificationModal(user) {
  // 游客不需要认证
  if (user.role === 'GUEST') return false

  // 所有非游客角色都需要检查实名认证状态
  // null = 未提交，REJECTED = 被拒绝可重新提交
  return user.certificationStatus === null || user.certificationStatus === 'REJECTED'
}
```

### 5. 各角色认证流程

```
普通用户 (REGULAR_USER)
  └── 实名认证 ──→ 审核通过 ──→ 可使用全部功能

企商用户 (ENTERPRISE_USER)
  ├── 实名认证 ──→ 审核通过
  └── 企业认证 ──→ 审核通过 ──→ 可使用全部功能

技艺大师 (MASTER_ARTISAN)
  ├── 实名认证 ──→ 审核通过
  └── 技艺认证 ──→ 审核通过 ──→ 可使用全部功能
```

### 6. 功能权限控制

```javascript
function canUseFullFeatures(user) {
  switch (user.role) {
    case 'GUEST':
      return false  // 游客只读

    case 'REGULAR_USER':
    case 'ENTERPRISE_USER':
    case 'MASTER_ARTISAN':
      // 必须实名认证通过
      return user.certificationStatus === 'APPROVED'

    case 'ADMIN':
    case 'SUPER_ADMIN':
      return true  // 管理员不受限

    default:
      return false
  }
}
```

---

## 接口参考

### 获取用户信息

```
GET /api/users/{userId}
Authorization: Bearer <token>
```

响应：
```json
{
  "id": 13,
  "username": "master_user",
  "role": "MASTER_ARTISAN",
  "avatarUrl": "https://...",
  "certificationStatus": "PENDING",
  "createdAt": "2026-05-10T10:00:00"
}
```

### 获取我的认证记录

```
GET /api/certifications/my
Authorization: Bearer <token>
```

响应：
```json
[
  {
    "id": 1,
    "certificationType": "REAL_NAME",
    "status": "APPROVED",
    "realNameVerified": true,
    "rejectReason": null,
    "createdAt": "2026-05-10T10:00:00",
    "updatedAt": "2026-05-10T12:00:00"
  },
  {
    "id": 2,
    "certificationType": "MASTER",
    "status": "REJECTED",
    "realNameVerified": false,
    "rejectReason": "证书照片不清晰",
    "createdAt": "2026-05-10T10:00:00",
    "updatedAt": "2026-05-10T14:00:00"
  }
]
```

---

## 完整示例：弹窗控制逻辑

```javascript
async function checkAndShowCertificationModal(userId, token) {
  const res = await fetch(`/api/users/${userId}`, {
    headers: { 'Authorization': `Bearer ${token}` }
  })
  const user = await res.json()

  switch (user.certificationStatus) {
    case null:
      // 从未提交过，弹窗引导认证
      showModal('请先完成实名认证', { showForm: true })
      break

    case 'REJECTED':
      // 被拒绝，弹窗显示原因并允许重新提交
      const certRes = await fetch('/api/certifications/my', {
        headers: { 'Authorization': `Bearer ${token}` }
      })
      const certs = await certRes.json()
      const rejected = certs.find(c => c.status === 'REJECTED')
      showModal(`认证被拒绝：${rejected?.rejectReason}`, {
        showForm: true,
        isResubmit: true
      })
      break

    case 'PENDING':
      // 审核中，不弹窗，显示提示
      showToast('您的认证正在审核中，请耐心等待')
      break

    case 'APPROVED':
      // 已通过，不做任何处理
      break
  }
}
```

---

## 当前问题（需前端修复）

**现状**：普通用户和企商用户首次登录时不会弹出实名认证弹窗，只有技艺大师会弹。

**原因**：前端弹窗逻辑可能只判断了 `role === 'MASTER_ARTISAN'`，没有覆盖所有角色。

**修复**：将弹窗条件改为基于 `certificationStatus` 判断（见上方第 4 节），而不是基于角色判断。

---

## 注意事项

1. **数据库自动迁移**：后端使用 `spring.jpa.hibernate.ddl-auto=update`，新列 `certification_status` 会在首次启动时自动创建，无需手动执行 SQL
2. **已有用户**：已注册但未提交过认证的用户，`certificationStatus` 为 `null`，与原有行为一致
3. **`@JsonIgnore`**：`User.password` 字段已标注 `@JsonIgnore`，不会出现在响应中
4. **兼容性**：如果前端暂时不使用此字段，不影响现有功能，只是弹窗行为不变
5. **弹窗触发时机**：建议在用户登录成功后、进入主页前检查 `certificationStatus`，而非每次刷新都检查（可用 localStorage 缓存已认证状态）
