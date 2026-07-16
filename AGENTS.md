# Agents 规则

本文件是本仓库的 Agents 通用入口，面向所有自动化编码代理和协作代理。用户指令优先于本文件；本文件优先于 `agents/` 下的专项规则。不要为了缩小 diff 弱化正确性、持久化安全或兼容性。

## 基础准则

1. 不确定就问，不要猜测。
2. 没有要求的不写，只写要求了的部分。
3. 以验收标准进行测试通过才算完成，别只给步骤。
4. 不适用的专项规则不强行套用；不确定是否适用时询问。

## 规则路由

1. 产出或改写正式文字时读取 `agents/WRITING_STYLE.md`，并完整执行其第四节写后链路；缺标题审定、机械扫描、人工判退、CV/TTR 或 AIGC-X 段落结果，任务未完成。
2. 执行开发任务时读取 `agents/instructions/development.instructions.md`。
3. `agents/instructions/development.instructions.md` 只作为索引，具体规则以相关专项 instruction 为准。
4. `agents/archive/` 和 `agents/prompts/archive/` 只作为历史任务参考，不自动作为当前规则。

## Git 与发布操作

1. 执行修改任务前检查远端主分支状态；可以 fast-forward 时先同步，存在冲突或需要人工判断时先询问。
2. 没有明确要求时，不主动 commit、push、rebase、创建 pull request 或执行其它会改变仓库历史和远端状态的 Git 操作。
3. 用户要求 git、提交、推送、拉取或发布时，先同步远端，再按请求操作；提交信息遵循既有格式，不添加 co-author trailer。
4. commit 前检查精确文件列表，并区分本次相关改动、无关用户改动和不应提交的本地文件。
5. 发现无关用户改动时，先询问是否一起提交，再按用户答复处理；不要静默排除、覆盖或丢弃用户改动。
6. `docs/plan/`、`logs/`、构建产物和 IDE 文件默认不提交，除非用户明确要求纳入。
7. 用户要求 commit 时，创建 commit 后必须 push 到当前分支；push 被拒绝或失败时，报告失败命令、原因和仓库状态，不把未推送 commit 说成已经完成。
8. 用户未要求 commit 时，完成后给一条符合项目既有格式的 commit 描述建议。
9. 除非明确要求，不使用破坏性 Git 命令。

## 执行流程

1. 原则上不新建 class，不添加 comments，除非 prompt 要求。
2. 与 IMYVMWorldGeo Core 高度协作，互相参考。

## 仓库卫生

1. 所有临时需求、执行计划、发现、进度日志和 agent 工作笔记放在 `docs/plan/`。
2. `docs/plan/` 是本地工作状态，不进入正式交付。
3. `logs/` 是运行输出，不进入正式交付。
4. 正式用户或 addon 文档放在 `docs/` 其它位置，属于正式交付内容。
5. `README.md` 只聚焦已发布行为和公开用法。
6. Git 文件选择按“Git 与发布操作”执行。
