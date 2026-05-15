---
name: community-geoscope-add
description: 添加聚落子 GeoScope 新增入口和收费规则
---

添加一个聚落子GeoScope新增按钮，放在Community Administration的Geometry Modification的对应模块下面。要做到：

1. 编码遵守`agents/instructions/development.instructions.md`中的规范。
2. 包含一个新的检查机制，Community对应的Region中新增GeoScope后的GeoScope数量，不可以超过正式成员（见GUIDELINES)总人数1/2,向下取整。
3. 包含完整收费机制。收费由新建一个行政区（对应GeoScope）的固定费用和收费机制中的价格参考修改面积时的TerritoryPricing机制组成。固定费用为配置项（见GUIDELINES)，一个Manor5000，一个Realm2500。
4. 原先对应Geometry Modification菜单的Global Modification按钮是没有作用的，现在按钮放置在其点进去之后的一个新Menu居中左的位置，显示为增加行政区。另增加一个删除行政区的按钮和一个移交行政区的按钮，但是暂时不要实现任何机制。对这个新增的Menu，一定要遵守(GUILDELINES 8.)
5. 按下增加行政区按钮时，仿照聚落创建总逻辑面板给出一个新增的Menu，可对新行政区进行命名和toggle形状，复用聚落创建逻辑中除了金额相关之外其他报错。
6. 按下确认时，仿照Geometry Modification中修改GeoScope面积的逻辑发送一份新的“地契账单”（实为确认消息），不过要添加新GeoScope的命名和形状信息在消息里面
7. 确认之后，首先检查聚落Assets，如果金额不足，提示玩家并报错；其次检查聚落人员数量和已经存在的区域数量。调用`ImyvmWorldGeo`的`PlayerInteractionApi`的`addScope`或最接近相关功能接口，如果创建成功，从聚落assets扣钱，并通过邮件系统通知所有聚落正式成员相关信息。消息通知参考GUIDELINES1.，传参数和语言文件中的参数使用一定要写对。
