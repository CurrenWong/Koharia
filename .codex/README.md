# Koharia Codex 子代理

项目级配置，参考 [LINUX DO 方案](https://linux.do/t/topic/2894461) 和 [官方子代理文档](https://learn.chatgpt.com/docs/agent-configuration/subagents)。主代理模型沿用用户选择；推荐的分工已写入根目录 `AGENTS.md`。

| 角色 | 模型 | 思考强度 | 默认权限 |
| --- | --- | --- | --- |
| default | gpt-5.6-terra | medium | read-only |
| quick_scan | gpt-5.6-luna | low | read-only |
| code_explorer | gpt-5.6-terra | high | read-only |
| reviewer | gpt-6-astra | high | read-only |
| verifier | gpt-5.6-terra | medium | workspace-write |
| mechanical_editor | gpt-5.6-luna | medium | workspace-write |

最多同时开启 3 个子代理，每个角色关闭继续派发的能力。编排规则要求主代理负责复杂实现，按需分配独立工作；Gradle 与设备操作须协调共享状态。

配置保存后，在本项目中新开会话以加载配置。是否提供角色选择取决于客户端实际工具；没有角色参数时，按 `AGENTS.md` 的兼容规则读取角色文件并显式传递指令和受支持的模型设置。仅靠任务名称不会自动应用角色配置。运行时权限可能覆盖文件中的沙箱默认值，不能将其当作强制安全隔离。

示例请求：`分析这个问题：让 code_explorer 追踪相关调用链，主代理同时检查现有测试，汇总后再实现。`

仅当明确要求创建独立任务时使用 `create_thread`。模型不可用时报告缺口，避免静默替换。此配置不修改全局设置，也不改变应用代码。

配置验证：7 个 TOML 文件通过 Python 3.11 `tomllib` 解析，六个角色的必需字段和禁止继续委派设置已检查。当前会话未验证客户端自动发现角色或真实模型调用；本地 CLI 0.155.1 的 `features` 子命令不支持 `--strict-config`，该命令不能作为 schema 验证依据。
