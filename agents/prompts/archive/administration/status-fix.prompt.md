---
name: community-status-fix
description: 修复聚落创建状态、PendingOperation 和权限状态问题
---

聚落创建流程中暴露了诸多问题，现在必须修改，请首先必须完全阅读并遵循`agents/instructions/development.instructions.md`，特别是其中涉及到代码机制，给合适的玩家对象提供消息和进行文档管理的部分，并修改以下内容:

## Realm的状态问题

请根据这个查证代码：按照标准聚落Creation流程，如果聚落创建执行时类型为`Realm`，则先进入一个招募玩家的`PendingOperation`流程，并处于一个`RECRUITNG_REALM`的状态，必须在限定时间内招募到足够成员(在`CommunityConfig`中有配置其数量)，然后能变成`AUDITING_REALM`，然后才能（由服务器而不是聚落的）管理员审核。

然而，这一过程现在执行时有如下问题:

1. 现在Realm创建一执行，也就是一进入`RECRUITING_REALM`状态，就直接给管理员发了审核通知邮件。
2. Realm就算招募到足够人数，也没有成为能够审核的状态，即转变为`AUDITING_REALM`并且在此时给管理员发邮件
3. 见下

## PendingOperation的问题

`PendingOperation`似乎没有有效保存。一旦服务器停止，`PendingOperation`就丢失了。

## Pending和Recruiting状态下的权限问题

按照规划来说，在以`PENDING`和`RECRUITING`开头的相关状态下的聚落，其聚落的Administration菜单下面的那些操作（不管是菜单进入还是以后的指令进入），除修改`Join Policy`和审核新成员加入外都应该在`entrypoint`和`application`的层次上完全**依照现有权限系统的机制**而完全停用。但实际上，除极少部分外，可以点进去随便修改，权限系统根本没应用，也没有一个清晰完整的权限配置`domain`位置让我清楚看到现在有哪些权限，实现方式是什么样，这就不好。

另外，分类意义上，应该把`PermissionCheck`和`territory`下面的机制放到一个domain下面统一的文件夹中，你认为呢？它们不是数据类，应该怎么命名呢？但是跟数据类应该放domain下面的两个文件夹，对不对？

## Manor的加入问题

Manor是有招募人数上限的，但是除`CommuntiyConfig`中有配置项之外，根本没在MemberManagement的相关模块中应用这个上限检查。希望Manor任何招人的情况下都应该应用这个检查，不管是发起申请和邀请的时候，还是聚落的admin审核通过申请的时候，都要检查。发起邀请和申请的时候如果有上限问题，应该通知操作者，不允许操作继续进行。审核通过申请的时候，应该通知操作者，并且不改动任何状态。
