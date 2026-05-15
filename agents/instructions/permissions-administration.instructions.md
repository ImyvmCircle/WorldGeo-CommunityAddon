---
applyTo: "src/main/kotlin/**/*.kt"
---

# 权限与行政规则

1. 正式成员仅包括 `OWNER`、`ADMIN`、`MEMBER`，不包括 `APPLICANT` 和 `REFUSED`。
2. 聚落行政操作必须检查 `AdministrationPermission(s)`、`PermissionCheck` 等权限入口。
3. 聚落行政操作同时调用 `canExecuteAdministration` 和 `canExecuteOperationInProto`。
4. 标准模式为 `executeWithPermission` 加两步检查。
