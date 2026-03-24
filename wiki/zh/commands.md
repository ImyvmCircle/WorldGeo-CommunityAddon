# 命令

> **导航：** [目录](index.md) | [主菜单](main-menu.md) | [领域菜单](community-menu.md) | [内政管理](administration.md) | [辖区](region.md) | [经济](economy.md) | [传送](teleport.md) | [内部频道](chat.md) | [命令](commands.md)

---

所有领域命令均使用根命令 `/community`。GUI 菜单是主要界面；命令提供了触发相同操作的替代或补充途径。

> **注意：** 所有由系统自动生成于聊天框中的**交互确认命令**（例如确认创建、确认改名、接受赠予等）使用单独的根命令 `/commun`，而非 `/community`。

`<communityIdentifier>` 可使用领域的**名称**或其**数字 ID**。

> **名称引号规则：** 仅由 ASCII 字母与数字（a–z、A–Z、0–9）组成的名称可直接输入。凡包含其他字符的名称——包括空格、中文、日文、韩文、带重音字母、符号等——在命令中输入时**必须用英文双引号括起来**，例如 `"我的领地"` 或 `"My Realm"`。此规则同时适用于 `<communityIdentifier>` 和所有 `<scopeName>` 参数。使用 Tab 键自动补全时，系统会自动为此类名称添加引号。

## 通用命令

| 命令 | 说明 | 对应菜单 |
|---|---|---|
| `/community` | 打开社群主菜单 | — |
| `/community help` | 在聊天框中显示帮助信息 | — |
| `/community list [type]` | 按类型筛选列出领域 | 浏览领域 |
| `/community query <communityIdentifier>` | 显示领域的地块信息 | 领域菜单 → 地块信息 |

**列表类型：** `all`（全部）· `joinable`（可加入）· `recruiting`（招募中）· `auditing`（审核中）· `active`（活跃）· `revoked`（已撤销）

---

## 圈地命令

| 命令 | 说明 | 对应菜单 |
|---|---|---|
| `/community select start` | 启用圈地模式 | 主菜单 → 圈地模式按钮 |
| `/community select stop` | 禁用圈地模式 | 主菜单 → 圈地模式按钮 |
| `/community select reset` | 清除所有已选点位 | 主菜单 → 重置圈地选择按钮 |

---

## 创建领域

| 命令 | 说明 | 对应菜单 |
|---|---|---|
| `/community create <shapeType> <communityType> <name>` | 初始化领域创建请求 | 主菜单 → 创建领域 |
| `/commun confirm_creation <regionId>` | 确认并完成创建 | 聊天提示 [确认] 按钮 |
| `/commun cancel_creation <regionId>` | 取消待处理的创建请求 | 聊天提示 [取消] 按钮 |

**形状类型：** `RECTANGLE`（矩形）· `CIRCLE`（圆形）· `POLYGON`（多边形）  
**领域类型：** `manor`（庄园）· `realm`（领地）

---

## 加入与退出

| 命令 | 说明 | 对应菜单 |
|---|---|---|
| `/community join <communityIdentifier>` | 加入领域（或按政策提交申请） | 浏览领域 → 点击 → 加入 |
| `/community leave <communityIdentifier>` | 退出领域 | 领域菜单 → 退出领域 |

---

## 内部频道

| 命令 | 说明 | 对应菜单 |
|---|---|---|
| `/community chat <communityIdentifier> <message>` | 向领域频道发送消息 | — |
| `/ch <communityIdentifier> <message>` | `/community chat` 的快捷别名 | — |
| `/community chat_channel <communityIdentifier>` | 切换该领域为活跃聊天频道 | 聊天室菜单 → 切换频道 |

---

## 内政公告

| 命令 | 说明 | 所需角色 |
|---|---|---|
| `/community announcement create <id> <content>` | 创建新公告 | 行政助理 / 所有者 |
| `/community announcement delete <id> <announcementId>` | 删除公告 | 行政助理 / 所有者 |
| `/community announcement list <id>` | 列出所有有效公告 | 任意成员 |
| `/community announcement view <id> <announcementId>` | 查看指定公告 | 任意成员 |

---

## /commun 交互确认命令

这些命令由系统自动在聊天框中生成为可点击的提示，通常不需要手动输入。所有此类命令均使用 `/commun` 前缀（而非 `/community`）。

### 地理修改确认

| 命令 | 说明 |
|---|---|
| `/commun confirm_modification <regionId> <scopeName>` | 确认几何修改 |
| `/commun cancel_modification <regionId> <scopeName>` | 取消几何修改 |
| `/commun confirm_setting <regionId>` | 确认地块设置修改 |
| `/commun cancel_setting <regionId>` | 取消地块设置修改 |
| `/commun confirm_teleport_point_set <regionId> <scopeName>` | 确认设置传送点 |

### 聚落改名确认

| 命令 | 说明 |
|---|---|
| `/commun confirm_rename <regionId> <nameKey>` | 确认改名 |
| `/commun cancel_rename <regionId> <nameKey>` | 取消改名 |

### 国库赠予确认

| 命令 | 说明 | 执行方 |
|---|---|---|
| `/commun accept_treasury_grant <regionId>` | 代表本聚落接受赠予 | 目标聚落领主或具有该授权的行政助理 |
| `/commun decline_treasury_grant <regionId>` | 代表本聚落拒绝赠予 | 目标聚落领主或具有该授权的行政助理 |
| `/commun cancel_treasury_grant <regionId>` | 取消本聚落发起的赠予请求 | 发起聚落领主或具有该授权的行政助理 |

国库赠予命令中，`<regionId>` 为**发起方聚落**的数字 ID。

### 成员邀请响应

| 命令 | 说明 | 执行方 |
|---|---|---|
| `/commun accept_invitation <communityIdentifier>` | 接受待处理的邀请 | 被邀请的玩家 |
| `/commun reject_invitation <communityIdentifier>` | 拒绝待处理的邀请 | 被邀请的玩家 |

---

## 服务器管理员命令

> **需要权限等级 2。**

| 命令 | 说明 |
|---|---|
| `/community audit <approve\|deny> <communityIdentifier>` | 批准或拒绝领域创建请求 |
| `/community force_active <communityIdentifier>` | 强制激活领域 |
| `/community force_revoke <communityIdentifier>` | 强制撤销领域 |
| `/community force_delete <communityIdentifier>` | 永久删除领域 |
| `/community announcement op list` | 列出所有领域的所有公告 |
| `/community announcement op delete <id> <announcementId>` | 强制删除任意公告 |
| `/community treasury deposit <communityIdentifier> <amount> [description]` | 向指定聚落国库充值（来源标记为"服务器管理员"） |
| `/community treasury withdraw <communityIdentifier> <amount> [description]` | 从指定聚落国库提款（来源标记为"服务器管理员"） |

- `<amount>` 为显示单位（如 `100.00` 即 $100.00）。
- `[description]` 为可选说明，会记录在国库账单中。

---

> **参见：** [目录](index.md) · [主菜单](main-menu.md)
