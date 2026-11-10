# 项目文档

先按任务找文档，不必通读。本文档描述当前实现；任务与验收进度只看 [TODO](../TODO.md)，构建约束以 [AGENTS.md](../AGENTS.md) 为准。

## 按任务阅读

| 要做什么 | 阅读 |
| --- | --- |
| 了解应用目标与功能 | [项目 README](../README.md) |
| 调整页面展示字段 | [当前页面字段](page-information.md) |
| 调整视觉、布局或交互 | [UI/UX 设计规范](Design.md) |
| 修改分层、状态、导航或转场 | [架构说明](architecture.md) |
| 接入模型、评测、中文发布包 | [ModelAtlas Data 契约](atlas-data.md) |
| 修改服务商解析、能力语义、报价或许可 | [数据源说明](data-source.md) |
| 修改 Room、目录事务、收藏或 Debug 数据编辑 | [本地目录数据](local-data.md) |
| 修改主题与偏好持久化 | [外观设置](appearance-settings.md) |
| 签名安装、双版本共存或 Debug 隔离 | [Debug / Release](debug-release-coexistence.md) |
| 选择测试范围、运行或核对结果 | [分级测试](testing.md) |
| 维护评测品牌素材 | [评测 Logo](benchmark-logos.md) |
| 调研待选评测与来源 | [候选 Benchmark](benchmarks.md)（不是接入清单） |

## 历史资料（按需查阅）

- [开发历史与原始验证](archive/development-history.md)：整理前 TODO 原文，含完成项、失败／复跑和历史规划，不代表当前状态。
- [旧评测协议](archive/legacy-benchmarks.md)：仅兼容回归，不用于当前生产同步。

## 维护约定

- 中文说明，保留代码标识、命令与路径；链接优先相对路径。
- **一项规则一个主文档**：页面字段归字段表，视觉归设计，状态流归架构，远程格式归接入契约，本地格式归存储，选测与命令归测试。
- 其他文档只保留理解所需摘要并链接主文档，不复制完整规则、完成状态或测试流水。
- 当前实现、候选方案、已验证事实和未验收事项明确区分；有冲突先查代码与证据，不自行改成“已完成”。
- 实现变化时更新对应专题与必要索引；历史不持续改写，后续结果另记，临时文件路径不作为长期证据可用性的承诺。
