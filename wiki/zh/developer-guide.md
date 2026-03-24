# Community Addon — 开发者指南

> **导航：** [首页](index.md) | [命令](commands.md) | **开发者指南**

---

本指南记录了参与 WorldGeo-CommunityAddon 模组开发时需要遵守的约定与重要注意事项。

---

## 依赖管理：本地源码 vs 已部署制品

本项目依赖 `ImyvmWorldGeo`（及相关库），这些依赖在构建时从 Maven 仓库拉取。**相邻目录下的本地源码（如兄弟项目 `ImyvmWorldGeo/`）不保证与实际构建或已部署服务器所使用的制品版本一致。**

**重要规则：**
- **不要**将兄弟项目的本地源文件视为规范的 API 参考。
- 实现调用 `ImyvmWorldGeo` API（如 `PlayerInteractionApi`、`RegionDataApi`）的功能时，请对照**已发布制品**验证方法签名（在 `gradle.properties` 中查看版本号，然后检查解析后的 jar 或 Maven 仓库）。
- 若基于与已部署制品不一致的本地源码编写代码，模组**将在运行时加载失败**，并抛出 `NoSuchMethodError` 等错误。

**如何检查实际制品：**
```bash
# 查看运行时依赖解析结果
./gradlew dependencies --configuration runtimeClasspath | grep imyvm

# 反编译 jar 以验证 API 签名
jar tf ~/.gradle/caches/.../imyvm-world-geo-*.jar
```

---

## 添加新的地理功能类型

新增 `GeographicFunctionType` 枚举值时：

1. 在 `domain/model/GeographicFunctionType.kt` 中添加枚举值。
2. 在 `CommunityRegionScopeMenu.generateMenuTitle()` 中为新类型添加 `when` 分支（菜单标题）。
3. 在 `CommunityRegionScopeMenuHandler.runExecuteScope()` 中添加 `when` 分支（处理选择逻辑）。
4. 若该功能不支持"全局"（一次性处理所有地块）操作，则按照 `SCOPE_DELETION` 和 `SCOPE_TRANSFER` 的做法在 `CommunityRegionScopeMenu` 中隐藏全局按钮。

---

## 添加新的待处理操作类型

待处理操作存储于 `WorldGeoCommunityAddon.pendingOperations: HashMap<Int, PendingOperation>`，以**来源领地 ID** 为键。

1. 在 `domain/model/PendingOperation.kt` 中添加新的 `PendingOperationType` 值（使用下一个顺序整数）。
2. 如需结构化数据，添加对应的伴生数据类（如 `XxxConfirmationData`）。
3. 在 `PendingOperation` 数据类中添加对应的可空字段。
4. 在 `PendingApplication.kt` 的 `addPendingOperation()` 中接收并传递新字段。
5. 在 `handleExpiredOperation()` 中添加过期消息处理分支。
6. 跨服务器重启时仅持久化 `inviterUUID`、`inviteeUUID` 和 `creationData`——其他字段均为有意设计的临时数据（5 分钟过期窗口使得持久化没有必要）。

---

## 社区列表的隐藏变种

`CommunityListMenu` 是标准的分页社区浏览器。当需要过滤后的子集（例如"除当前社区外的所有社区"）时，**不要修改** `CommunityListMenu` 本身。应新建一个文件，复用分页布局，加入自定义过滤条件和 `onCommunitySelected` 回调。参考实现见 `ScopeTransferTargetListMenu.kt`。

---

## 翻译键

所有面向用户的字符串均通过 `Translator.tr(key, vararg args)` 处理。占位符使用 `{0}`、`{1}`、`{2}` 等。

- 中文：`src/main/resources/assets/community/lang/zh_cn.json`
- 英文：`src/main/resources/assets/community/lang/en_us.json`

**两个文件都必须添加翻译键。** 相关键放在一起（例如所有 `community.scope_transfer.*` 键紧邻 `community.scope_delete.*` 键）。

> **单引号转义：** 凡是翻译值中含有单引号（如英文的 `it''s`、`don''t`），且该条目以带参数方式调用（含 `{0}` 等占位符），**必须**将单引号转义为 `''`（两个单引号）。这是 `java.text.MessageFormat` 的要求；无参数条目不受影响。

> **消息格式：** 发送给玩家的文本需使用 MOTD 格式（颜色、加粗、下划线等）制作视觉效果，但不要引入 Unicode 特殊符号。枚举类型等变量值应转换为人类可读文本（如 `manor`、`rectangle`），不要直接放入消息，也不要用单引号包围参数（否则参数将无法显示）。

> **翻译名称：** 专名等翻译需仔细对比旧有文档；实在不确定应向操作者提问，不要自行猜测。

---

## 配置项

- **`CommunityConfig`**：存储本 mod 所有非定价配置项。任何具体数值都应写入此文件。
- **`PricingConfig`**：存储所有定价相关配置（创建价格、加入费用、面积定价、权限定价系数等）。新增定价系数必须写入此文件。

---

## 数据库持久化

`CommunityDatabase` 是数据库维护类。凡涉及修改 `Community` 成员变量的操作，**必须**检查是否需要同步修改数据库存储逻辑。

---

## 待处理操作与确认流程

所有需要在有限时限内作出反应的任务均通过 `PendingOperation` 实现，由 `PendingApplication` 集中管理。创建任何 `PendingOperation` **必须**通过 `pendingApplication` 中心化管理，不得绕过。

---

## 领域权限检查

涉及领域权限的内容，必须检查对应操作入口是否与 `AdministrationPermission(s)` 和 `PermissionCheck` 等相关类相联系并实现了权限检查。

**对于任何聚落行政操作，必须同时调用：**
- `canExecuteAdministration`（检查角色和权限）
- `canExecuteOperationInProto`（检查聚落状态）

两者缺一不可。参照 `runAdmRegion` 中的实现模式（`executeWithPermission` + 两步检查）作为标准范例。

---

## 菜单开发规范

- **关闭面板：** 点击 Menu 按钮后要发送文本消息给玩家时，必须先关闭面板，玩家才能看到消息。需合理规划流程，避免关闭后某些后续面板功能玩家无法到达。
- **runBack 逻辑：** 任何新增 Menu **必须**写好完整的 `runBack` 相关逻辑。
- **列表菜单：** 本 mod 有 `AbstractListMenu`，是所有列表 Menu 的默认实现，新增列表类菜单须继承它。
- **按钮实现分离：** `entrypoint/screen` 目录下所有 Menu 的 `addButton(){}` 方法内的函数化参数**不得**直接实现，必须在 `application/screen` 下对应功能目录及文件中实现。实现前应检查该模块文件是否已存在。
- **玩家列表：** 任何玩家列表默认使用正版头颅解析显示，参考 `CommunityMemberListMenu` 中的实现。
- **Toggle 开关：** 参考 `MainMenu` 中 Selection Mode 开关及其对应 Handler 的实现范例（本质是修改数据并重新加载当前页面）。

---

## 命令注册

所有命令在 `CommandRegister` 中的 `register()` 函数里注册，在同一文件中提取参数并调用 application 层对应实现。命令参数中**不应**以任何 id（如 `regionNumberId`、`announcementId`）作为唯一可用参数，因为这些 id 不是人类可读的；确实需要时，参照其他 Provider 提供对应的 SuggestionProvider。

> **非 ASCII 名称引号规则：** 命令参数中涉及 Region 名称、GeoScope 名称或 Community 名称的 SuggestionProvider，对于不满足"全部字符均为 ASCII 字母或数字"的名称，必须用双引号包裹后再 suggest（即 `if (!name.all { it.isLetterOrDigit() && it.code < 128 }) builder.suggest("\"$name\"") else builder.suggest(name)`）。

---

## 金钱处理

所有金钱操作均通过 `EconomyMod` 实现，内部单位为 `Long`；转换为玩家可读格式需除以 100 并保留两位小数（`"%.2f".format(amount / 100.0)`）。

---

## 其他规范

- 原则上不要新建新的 class，也不要添加 Comments。
- 修改机制后，**必须**检查并更新 `README.md`，以玩家侧的游戏机制介绍为主，不要过度暴露游戏实现。
- 测试须包含 `./gradlew runServer`。
- 不得使用 git。
- 未说明清楚的机制、语言文件用名、感到机制模糊的地方等应向操作者提问，不要为了确认需求终止对话，不要自行猜测。
- 本项目与 IMYVMWorldGeo Core 高度协作，互相参考。
