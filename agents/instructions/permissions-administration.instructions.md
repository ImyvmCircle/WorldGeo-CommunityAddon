---
applyTo: "src/main/kotlin/**/*.kt"
---

# 权限与行政规则

1. 正式成员仅包括 `OWNER`、`ADMIN`、`MEMBER`，不包括 `APPLICANT` 和 `REFUSED`。
2. 聚落行政操作必须检查 `AdministrationPermission(s)`、`PermissionCheck` 等权限入口。
3. 聚落行政操作同时调用 `canExecuteAdministration` 和 `canExecuteOperationInProto`。
4. 标准模式为 `executeWithPermission` 加两步检查。
5. 实现或改动 Community 中涉及地皮、聚落区域或权限转移并伴随付款的交易时，按两阶段执行：先冻结交易事实，包括价格、双方、地皮或区域编号；再执行扣款、入账和 WorldGeo 权限写入。权限写入失败时必须回滚资金或保留待处理记录，不允许出现“钱已付、权未转”且无记录可查的状态。交易后原所有者的地皮权限被清除；若原所有者属于该地皮所属社区的管理人员，其权限保留；建筑保持不动。
