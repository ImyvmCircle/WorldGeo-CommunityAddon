# 开发规范（WorldGeo-CommunityAddon 项目上下文）

> 配套文档：[写作规范](prompts/WRITING_STYLE.md) · [迭代规则](prompts/ITERATION_RULES.md) · [AI 规则](prompts/AI_RULES.md)

## AI 执行纪律

执行任何任务时，以下纪律优先于一切：

1. **不确定就问，别猜** — 机制不明、需求模糊时，向操作者提问；不要为确认需求而终止对话。
2. **没要求的不写** — 仅实现 prompt 明确要求的内容。
3. **只改被要求的部分** — 不修改 prompt 未涉及的代码或文档。
4. **给验收标准，别给步骤** — 完成后说明结果是否满足预期，而非描述执行过程。

## 通用开发规范

每次完成任务前后，必须逐条检查任务方案和执行是否符合以下规范：

1. **i18n**：通过 `Translator.tr()` 实现，中英文语言文件同步维护。原则上不使用 `Text.literal()`。对发送给玩家的文本，使用 MOTD 格式的颜色和样式，不引入 Unicode 特殊符号。不要用单引号包裹参数占位符。凡语言文件条目值中含有单引号（如 `it's`、`don't`），且以带参数方式调用，必须将 `'` 转义为 `''`；`java.text.MessageFormat` 将 `'` 视为转义字符，未转义会导致占位符或后续内容被错误解析。无参数调用不受此影响，但建议统一转义以防将来添加参数时遗漏。

2. **配置**：所有具体数值写入对应配置类，不在业务代码中硬编码。

3. **持久化**：凡涉及修改数据成员变量的操作，必须检查是否需要同步更新数据库存储。

4. **命令注册**：所有 Command 在 `CommandRegister.register()` 中注册，参数提取在同一文件完成，调用 application 层实现；无合适调用时，自行实现对应模块。

5. **SuggestionProvider**：涉及名称参数的所有 SuggestionProvider，不满足「全部字符为 ASCII 字母或数字」的名称必须用双引号包裹后 suggest：
   ```kotlin
   if (!name.all { it.isLetterOrDigit() && it.code < 128 }) builder.suggest("\"$name\"") else builder.suggest(name)
   ```

6. **代码规范**：原则上不新建 class，不添加 Comments。

7. **README.md**：修改机制后必须更新 `README.md`，不要过度暴露游戏实现，以玩家侧的游戏机制介绍为主，不使用 emoji 等特殊 Unicode 符号。每次任务完成前须确认 changelog 已同步记录本次更改；没有明确指示不新建版本、不更新版本号；版本更改描述简洁。

8. **版本控制**：不使用 git，除非 prompt 明确要求。提交时遵循 git log 中已有的 commit 格式，不添加 Co-authored-by 等 trailer。

9. **测试**：测试必须包含 `./gradlew runServer`。

## 项目特有规范

1. **配置**：`CommunityConfig` 存储所有非定价配置；`PricingConfig` 存储所有定价相关配置（创建价格、加入费用、面积定价、权限定价等系数），新增任何定价系数也应写入该文件。

2. **持久化**：`CommunityDatabase` 负责所有持久化。凡涉及修改 `Community` 成员变量的操作，必须检查是否需更新数据库。

3. **PendingOperation**：所有需在时限内作出反应的任务通过 `PendingApplication` 中心化管理，创建任何 `PendingOperation` 必须经由 `pendingApplication`。主要用于确认逻辑。

4. **成员身份**：领域正式成员由 `MemberRoleType` 定义，不含 `APPLICANT` 和 `REFUSED`；谈论是否在某领域时，默认只包含 `OWNER`、`ADMIN`和`MEMBER`。

5. **权限检查**：涉及领域权限的操作，必须检查对应操作入口是否与 `AdministrationPermission(s)` 和 `PermissionCheck` 等相关类联系并实现了权限检查。**对于任何聚落行政操作，必须同时调用 `canExecuteAdministration`（检查角色和权限）和 `canExecuteOperationInProto`（检查聚落状态），两者缺一不可，不允许自行决定只检查其中某一项。** 参照 `runAdmRegion` 中的实现模式（`executeWithPermission` + 两步检查）作为标准范例。

6. **Menu 系统**：
   - 任何新 Menu 必须写完整的 `runBack` 逻辑。
   - 列表 Menu 使用 `AbstractListMenu`。
   - `entrypoint/screen` 中 `addButton(){}` 的函数化参数必须在 `application/screen` 对应目录实现，实现前检查模块文件是否已存在。
   - 点击 Menu 后需发送文本给玩家时，先关闭面板，并合理规划流程避免玩家无法到达后续面板。
   - toggle 开关参照 `MainMenu` Selection Mode 及其 Handler（修改数据后重新加载本页面）。

7. **玩家列表**：任何玩家列表默认显示正版头颅，参考 `CommunityMemberListMenu`。

8. **命令参数**：命令参数不以不可读 ID（如 `regionNumberId`、`annoucementId`）作为唯一参数；如必须使用，在 `register()` 中参考其他 Provider 实现一个 Provider。

9. **金钱**：涉及金钱的操作，单位为 `Long`（`EconomyMod`），显示时除以 100 并保留两位小数。

10. **枚举值**：`MANOR`、`RECTANGLE` 等枚举值在消息中需转为人类可读文本（如 `manor`、`rectangle`），不直接放入消息。翻译名称特别是专名，需仔细对比旧有文档；不知情时询问，而不是瞎编。

11. **ImyvmWorldGeo API 依赖**：从 Maven 拉取制品，本地源码**不保证**与构建版本一致；调用 API 时对照**已发布制品**验证签名（查 `gradle.properties` 版本号，检查解析后的 jar），否则运行时将抛出 `NoSuchMethodError`。

12. **SuggestionProvider 范围**：Region 名称、GeoScope 名称及 Community 名称均须应用通用规范中的双引号包裹规则，原因是包含中文等非 ASCII 字符或空格的名称在 Brigadier 命令解析中若不加引号将无法被正确识别。

13. **协作**：本项目与 IMYVMWorldGeo Core 高度协作，互相参考。
