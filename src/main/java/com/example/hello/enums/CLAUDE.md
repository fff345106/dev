[根目录](../../../../CLAUDE.md) > [src](../../) > [main](../) > [java](../) > [com.example.hello](../) > **enums**

# Enums 模块

## 模块职责

枚举定义，包含业务常量、编码规则和状态值。

## 枚举清单

| 枚举 | 职责 |
|------|------|
| `UserRole` | 用户角色（SUPER_ADMIN/ADMIN/USER/GUEST） |
| `AuditStatus` | 审核状态（PENDING/APPROVED/REJECTED） |
| `PatternCodeEnum` | 纹样编码体系（主类别、子类别、风格、地区、时期） |
| `ImageSourceType` | 图片来源类型（TEMP_UPLOAD/EXTERNAL/LIBRARY） |

## PatternCodeEnum 详细结构

### 主类别（MainCategory）
- AN（动物）、PL（植物）、PE（人物）- 有子类别
- LA（风景）、AB（抽象）、OR（器物）、SY（符号）、CE（庆典）、MY（神话）、OT（其他）- 无子类别

### 子类别
- `AnimalSubCategory` - BD/FS/IN/MA/MY/RP/OT
- `PlantSubCategory` - FL/TR/FR/GR/LV/OT
- `PersonSubCategory` - MU/FE/CH/EL/CE/OT

### 风格（Style）
- TR（传统）、MO（现代）、FO（民间）、ET（民族）、GE（几何）、RE（写实）、DE（装饰）、MI（混合）、OT（其他）

### 地区（Region）
- CN（中国）、BJ（北京）、TJ（天津）、HB（河北）、SX（山西）、SD（山东）、JS（江苏）、ZJ（浙江）、AH（安徽）、FJ（福建）、GD（广东）、SC（四川）、YN（云南）、OT（其他省份）

### 时期（Period）
- XS（先秦）、QG（秦汉）、WS（魏晋）、TG（隋唐）、SG（宋元）、MG（明清）、MJ（民国）、XD（现代）、OT（其他时期）

## 相关文件

- `UserRole.java` - 用户角色枚举
- `AuditStatus.java` - 审核状态枚举
- `PatternCodeEnum.java` - 纹样编码枚举（含所有子类别）
- `ImageSourceType.java` - 图片来源类型枚举
