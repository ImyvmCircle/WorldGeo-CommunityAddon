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
