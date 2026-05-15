---
applyTo: "src/main/kotlin/com/imyvm/community/infra/**,src/main/kotlin/com/imyvm/community/domain/model/community/**,src/main/kotlin/**/*.kt"
---

# 持久化与 PendingOperation 规则

1. `Community` 数据变化同步检查 `CommunityDatabase`。
2. 新增持久化字段遵守 `data-compatibility.instructions.md`。
3. `PendingOperation` 通过 `pendingApplication` 集中管理。
4. 任何限时确认逻辑都归入 PendingOperation 机制。
