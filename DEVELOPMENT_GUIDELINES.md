# 通用上下文

1. 本mod有i18n系统，通过`Translator.tr()`函数实现，以下说的所有按钮目前实现的名称均为英文，请不要做中文文件。原则上不要使用`Text.literal()`。
2. `CommunityConfig`里面存储了本mod的所有配置项。
3. `CommunityDatabase`是数据库的维护类。凡是涉及改动`Communtiy`成员变量的操作，必须要看看是否涉及数据库存储的改动。
4. `PendingOperation`涉及所有需要在一定时限内作出反应的任务，实现由`PendingApplication`做出。
5. 聚落的正式成员是由`MemberRoleType`定义的，不包含`APPLICANT`和`REFUSED`。谈论是否在某个聚落的时候，默认只包含`OWNER`、`ADMIN`和`MEMBER`。
6. 涉及到聚落权限的内容，必须检查修改对应操作入口是否与`AdministrationPermission(s)`和`PermissionCheck`等相关类相联系并实现了权限检查。
7. 对于`Menu`点击后要发送文本消息给玩家的情况，请记得关闭面板本身玩家才能看到文本消息。这中间，一定要合理规划流程，避免关闭使得某些后面的面板功能实质上玩家无法到达。
8. 本mod有一个`AbstractListMenu`，是所有列表Menu的默认实现。
9. `entrypoint/screen`目录下的所有`menu`的`addButton(){}`方法里面的函数化参数都不是直接实现的，而必须在`application/screen`下面的对应功能目录及文件实现。实现前，应检查该模块文件是否已经添加。
10. 任何Command都在`CommandRegister`中的`register()`函数里面注册，并在同一文件中提取参数，并调用application对应实现。没有找到合适的调用的时候，要自己实现模块。
11. 任何Command最好不要以任何形式的id为某个参数位置上的唯一可用参数，比如`regionNumberId`和`annoucementId`，因为这些Id设计上不是人类可读的。实在需要使用的话，请在register()函数中参考别的Provider提供一个Provider。
12. 任何玩家列表默认有正版的头颅解析显示。请参考`CommunityMemberListMenu`中玩家的列表。
13. 我们这个项目中所说的toggle开关的实现范例是`MainMenu`中的Selection Mode开关及其对应`Handler`实现。其实质是修改数据，并重新根据修改后的数据加载本页面。
14. 修改机制之后，必须检查`README.md`进行修改。