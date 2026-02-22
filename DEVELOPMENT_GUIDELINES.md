# 通用上下文

每次完成任务前后，必须逐条检查任务完成方案和执行是否符合以下开发规范：

1. 本mod有i18n系统，通过`Translator.tr()`函数实现，以下说的所有按钮目前实现的名称均为英文，请不要做中文文件。原则上不要使用`Text.literal()`。需实现`resource`里面对应的英文项目。对于发送给玩家的文本，需用MOTD格式制作比较美观的色彩和加粗下划线等效果，但不要引入Unicode特殊符号。对于`MANOR`和`RECTANGLE`等变量值，请用人类可读文本，转化成`manor`或`rectangle`等正常词汇文本引入消息，而不要直接放入消息。不要使用单引号包围参数，否则参数无法显示。
2. `CommunityConfig`里面存储了本mod的所有配置项。任何的具体数值，都应该写进去。`CommunityPricingConfig`里面存储了所有定价相关的系数，以后新增任何定价系数也应写入该文件。
3. `CommunityDatabase`是数据库的维护类。凡是涉及改动`Communtiy`成员变量的操作，必须要看看是否涉及数据库存储的改动。
4. `PendingOperation`涉及所有需要在一定时限内作出反应的任务，实现由`PendingApplication`做出，创建任何`PendingOperation`一定要通过`pendingApplication`中心化管理。这主要用于确认逻辑。
5. 聚落的正式成员是由`MemberRoleType`定义的，不包含`APPLICANT`和`REFUSED`。谈论是否在某个聚落的时候，默认只包含`OWNER`、`ADMIN`和`MEMBER`。
6. 涉及到聚落权限的内容，必须检查修改对应操作入口是否与`AdministrationPermission(s)`和`PermissionCheck`等相关类相联系并实现了权限检查。
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
17. 修改机制之后，必须检查`README.md`进行修改。不要过度暴露游戏实现，以玩家侧的游戏机制介绍为主。