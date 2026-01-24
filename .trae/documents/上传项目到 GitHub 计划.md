我将为您实现批量审核功能。

### 核心变更

1. **新增 DTO**: `BatchAuditRequest`，用于接收批量审核的请求参数（ID列表、审核结果、拒绝原因）。
2. **Service 层**: 在 `AuditService` 中增加 `batchAudit` 方法，复用现有的单条审核逻辑，并添加事务控制以保证数据一致性。
3. **Controller 层**: 在 `AuditController` 中增加 `/batch-review` 接口。

### 详细步骤

#### 1. 创建数据传输对象 (DTO)

创建 `src/main/java/com/example/hello/dto/BatchAuditRequest.java`:

```java
public class BatchAuditRequest {
    private List<Long> ids;      // 待审核的ID列表
    private Boolean approved;    // 是否通过
    private String rejectReason; // 拒绝原因
    // getters and setters
}
```

#### 2. 扩展业务逻辑 (Service)

在 `src/main/java/com/example/hello/service/AuditService.java` 中添加:

```java
@Transactional
public void batchAudit(BatchAuditRequest request, Long auditorId) {
    AuditRequest singleRequest = new AuditRequest();
    singleRequest.setApproved(request.getApproved());
    singleRequest.setRejectReason(request.getRejectReason());

    for (Long id : request.getIds()) {
        audit(id, singleRequest, auditorId); // 复用现有的单条审核逻辑
    }
}
```

#### 3. 新增 API 接口 (Controller)

在 `src/main/java/com/example/hello/controller/AuditController.java` 中添加:

```java
@PostMapping("/batch-review")
public ResponseEntity<?> batchAudit(@RequestBody @Valid BatchAuditRequest request) {
    Long adminId = 1L; // 暂时使用模拟管理员ID
    auditService.batchAudit(request, adminId);
    return ResponseEntity.ok().build();
}
```

