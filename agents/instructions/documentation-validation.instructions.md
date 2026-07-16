---
applyTo: "README.md,agents/**/*.md"
---

# 文档与验证规则

1. 正式文字先读取 `agents/WRITING_STYLE.md`。
2. 用户侧机制变化同步 README 和 changelog。
3. changelog 描述保持简洁；不自行新建版本，不自行更新版本号。
4. 调用外部或并列项目 API 时，以 Gradle 解析的已发布制品为准。
5. 代码修改使用 `./gradlew build`；运行时机制、命令、菜单、持久化、金钱、权限或集成行为变化包含 `./gradlew runServer`。
