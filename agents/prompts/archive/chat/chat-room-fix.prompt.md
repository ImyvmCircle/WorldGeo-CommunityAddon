---
name: community-chat-room-fix
description: 修复聚落聊天室切换、前缀、历史与权限表现
---

聊天室未按预期运作。请检查下面的这些并且实现。请务必做到agents/instructions/development.instructions.md的要求.

1. 切换聊天状态无效。且其界面设计非常糟糕。这里要重点说的是，我不需要一个切换聊天室权限按钮，我是想做一个切换是否默认发送消息就是往该聚落聊天室里面发送的按钮，这样避免每次都要打大段指令，很麻烦。请将那个按钮和其所有实现全部移除，替换为我所说的这个。
2. 另外，消息前缀有点太单调了。请打上颜色标签。不同聚落用不同颜色，用其ID加一个比较有特色的随意算法对应minecraft里面motd标签。职位不要引用原文，OWNER称'Lord'(realm)和'Landowner'(manor);相对应的ADMIN称Steward和HouseKeeper;MEMBER称Citizen和Resident。整个前缀都要装饰性非常强

4. Menu中不同按钮使用了同一个物品，布局也不好。
5. 聊天历史不应该由Menu实现，而是应该由可交互的聊天框实现。
6. 修复标题，修复当聚落列表为空的时候点击自己聚落会出现：Internal Exception:
    io.netty.handler.codec.EncoderException: Failed
     to encode packet 'clientbound/minecraft:open_screen' ，这个重点在于语言项目资源未添加。
7. 玩家加入聚落没有正确扣钱。玩家主动加入聚落要扣玩家的钱，申请加入的申请发出的时候就要扣钱（被邀请的除外）。如果申请不通过，则退钱。重新整理这些机制。
8. 检查加入机制是否是这样：JOIN_POLICY在任何情况下都可以邀请。只有JOIN_POLICY 为 APPLICANTION时候需要审核。
9. 我认为任何形式下玩家加入(申请时，审核时，OPEN的JOIN_POLICY下直接加入时)或退出聚落都应该发信件给聚落的“政要”，即OWNER ADMIN和另一个维度上的Councilor。
