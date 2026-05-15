---
applyTo: "src/main/kotlin/**/screen/**,src/main/kotlin/**/menu/**"
---

# 菜单与界面规则

1. 菜单点击后如需让玩家阅读聊天消息，先关闭菜单。
2. 新增菜单必须有完整 `runBack`。
3. 列表菜单优先复用 `AbstractListMenu`。
4. `entrypoint/screen` 菜单按钮逻辑放在对应 `application/screen` 模块。
5. 玩家列表参考 `CommunityMemberListMenu` 显示正版头颅。
6. toggle 逻辑参考 Main Menu selection mode，修改数据后重载页面。
