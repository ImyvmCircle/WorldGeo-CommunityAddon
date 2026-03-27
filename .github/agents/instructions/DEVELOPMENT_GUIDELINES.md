# 通用上下文

每次完成任务前后，必须逐条检查任务完成方案和执行是否符合以下开发规范：

1. 本mod有i18n系统，通过`Translator.tr()`函数实现，以后中英文文件都要做。原则上不要使用`Text.literal()`。需实现`resource`里面对应的英文项目。对于发送给玩家的文本，需用MOTD格式制作比较美观的色彩和加粗下划线等效果，但不要引入Unicode特殊符号
   - 对于`MANOR`和`RECTANGLE`等变量值，请用人类可读文本，转化成`manor`或`rectangle`等正常词汇文本引入消息，而不要直接放入消息。不要使用单引号包围参数，否则参数无法显示。
   - **凡是语言文件条目值中含有单引号 `'`（如英文中的 `it's`、`don't` 等），且该条目以带参数的方式调用（即传入 `{0}` 等占位符），必须将单引号转义为 `''`（两个单引号）。这是因为 `java.text.MessageFormat` 将 `'` 视为转义字符，未转义的单引号会导致占位符或后续内容被错误解析。无参数调用的条目不受此影响。**
   - 翻译名称，特别是专名名称，需要仔细对比旧有文档，实在不知情应该询问，而不是瞎编。
2. `CommunityConfig`里面存储了本mod的非定价的所有配置项。任何的具体数值，都应该写进去。`PricingConfig`里面存储了所有定价相关的配置（包括创建价格、加入费用、面积定价、权限定价等系数），以后新增任何定价系数也应写入该文件。
3. `CommunityDatabase`是数据库的维护类。凡是涉及改动`Communtiy`成员变量的操作，必须要看看是否涉及数据库存储的改动。
4. `PendingOperation`涉及所有需要在一定时限内作出反应的任务，实现由`PendingApplication`做出，创建任何`PendingOperation`一定要通过`pendingApplication`中心化管理。这主要用于确认逻辑。
5. 领域的正式成员是由`MemberRoleType`定义的，不包含`APPLICANT`和`REFUSED`。谈论是否在某个领域的时候，默认只包含`OWNER`、`ADMIN`和`MEMBER`。
6. 涉及到领域权限的内容，必须检查修改对应操作入口是否与`AdministrationPermission(s)`和`PermissionCheck`等相关类相联系并实现了权限检查。**对于任何聚落行政操作，必须同时调用`canExecuteAdministration`（检查角色和权限）和`canExecuteOperationInProto`（检查聚落状态），两者缺一不可，不允许自行决定只检查其中某一项。**参照`runAdmRegion`中的实现模式（`executeWithPermission` + 两步检查）作为标准范例。
7. 对于`Menu`点击后要发送文本消息给玩家的情况，请记得关闭面板本身玩家才能看到文本消息。这中间，一定要合理规划流程，避免关闭使得某些后面的面板功能实质上玩家无法到达。
8. 任何`Menu`新增一定要写好完整的`runBack`相关逻辑。
9. 本mod有一个`AbstractListMenu`，是所有列表Menu的默认实现。
10. `entrypoint/screen`目录下的所有`menu`的`addButton(){}`方法里面的函数化参数都不是直接实现的，而必须在`application/screen`下面的对应功能目录及文件实现。实现前，应检查该模块文件是否已经添加。
11. 任何Command都在`CommandRegister`中的`register()`函数里面注册，并在同一文件中提取参数，并调用application对应实现。没有找到合适的调用的时候，要自己实现模块。
12. 任何Command最好不要以任何形式的id为某个参数位置上的唯一可用参数，比如`regionNumberId`和`annoucementId`，因为这些Id设计上不是人类可读的。实在需要使用的话，请在register()函数中参考别的Provider提供一个Provider。
13. 任何玩家列表默认有正版的头颅解析显示。请参考`CommunityMemberListMenu`中玩家的列表。
14. 我们这个项目中所说的toggle开关的实现范例是`MainMenu`中的Selection Mode开关及其对应`Handler`实现。其实质是修改数据，并重新根据修改后的数据加载本页面。
15. 任何涉及到金钱的操作，都要明白金钱以`EconomyMod`实现，单位是`Long`，且转换为玩家可读的格式需要除以100并保留两位小数点。
16. 原则上不要新建新的class，也不要添加Comments.
17. 修改机制之后，必须检查`README.md`进行修改。不要过度暴露游戏实现，以玩家侧的游戏机制介绍为主。每次完成任务前，须确认`README.md`的changelog部分已同步记录本次更改，但不要随意新建版本或更新版本号。
18. 不使用git,除非prompt要求。进行prompt提交时，应符合git log里面先前的一般commit格式，简洁规范。
19. 本项目依赖`ImyvmWorldGeo`从Maven仓库拉取制品，相邻目录下的本地源码**不保证**与实际构建所使用的制品版本一致。实现调用`ImyvmWorldGeo` API的功能时，请对照**已发布制品**验证方法签名（在`gradle.properties`中查看版本号，然后检查解析后的jar），不要将本地源文件视为规范的API参考，否则将在运行时抛出`NoSuchMethodError`等错误。
20. 测试要包含./gradlew runServer.
21. 未说明清楚的机制、语言文件用名和感到机制模糊的地方等等应该向操作者提问。不要为了确认需求终止对话。
22. 命令参数中涉及 Region 名称、GeoScope 名称或 Community 名称的所有 SuggestionProvider，必须对不满足"全部字符均为 ASCII 字母或数字"条件的名称用双引号包裹后再 suggest，即使用 `if (!name.all { it.isLetterOrDigit() && it.code < 128 }) builder.suggest("\"$name\"") else builder.suggest(name)` 的形式。这是因为包含中文等非 ASCII 字符或空格的名称，在 Brigadier 命令解析中若不加引号将无法被正确识别。
23. 本项目跟IMYVMWorldGeo Core要高度协作，互相参考。