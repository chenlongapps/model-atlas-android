# 项目文档

按任务阅读，不必通读。专题描述当前实现与约束，不代表已验收。

- **执行规则**：[AGENTS.md](../AGENTS.md)（构建、修改边界、分级验证）。
- **当前任务与待验收**：[TODO.md](../TODO.md)；应用目标与功能见[项目 README](../README.md)。
- **历史证据**：[开发历史](archive/development-history.md)，仅按需查阅，不作为当前实现依据。

## 按任务阅读

| 要做什么 | 阅读 |
| --- | --- |
| 调整页面展示字段 | [当前页面字段](page-information.md) |
| 调整视觉、布局或交互 | [UI/UX 设计规范](Design.md) |
| 修改分层、状态、导航或转场 | [架构说明](architecture.md) |
| 接入 LLM API、流式请求、配置与取消 | [LLM API 基础接入](llm-api.md) |
| 修改 AI 聊天、会话状态与键盘交互 | [AI 文本聊天](chat.md) |
| 修改 AI 配置 DataStore、Keystore 加密、删除与备份 | [AI 配置持久化](ai-configuration.md) |
| 接入模型、评测、中文发布包 | [ModelAtlas Data 契约](atlas-data.md) |
| 修改服务商解析、能力语义、报价或许可 | [数据源说明](data-source.md) |
| 修改 Room、目录事务、收藏或 Debug 数据编辑 | [本地目录数据](local-data.md) |
| 修改主题、底栏自动隐藏与偏好持久化 | [外观设置](appearance-settings.md) |
| 签名安装、双版本共存或 Debug 隔离 | [Debug / Release](debug-release-coexistence.md) |
| 选择测试范围、运行或核对结果 | [分级测试](testing.md) |
| 维护评测品牌素材 | [评测 Logo](benchmark-logos.md) |

## 维护约定

- 中文短句，保留代码标识、单位、命令和明确路径；优先相对链接与可检索标题。
- 一项规则只在主文档维护，其他位置保留摘要和链接；不复制完整契约或测试流水。
- 区分当前实现、规划、实际验证与待验收；有冲突先查代码和证据，不自行标记完成。
- 实现变化同步专题与索引；历史只追加，不改写旧结论。临时证据路径不保证长期可用。
