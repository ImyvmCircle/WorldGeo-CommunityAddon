---
applyTo: "src/main/kotlin/**/config/**,src/main/kotlin/**/pricing/**,src/main/kotlin/**/*.kt"
---

# 配置与定价规则

1. 非定价具体数值放入 `CommunityConfig`，不在业务代码中硬编码具体数值。
2. 定价具体数值放入 `PricingConfig`，新增定价系数写入 `PricingConfig`。
3. EconomyMod 金钱以 `Long` 存储，展示时除以 100 并保留两位小数。
4. 文档中的价格使用玩家可见金额，不使用底层 `Long` 单位。
