# ModelAtlas Data 接入契约

## 当前实现

模型、评测成绩／定义和中文描述使用 [ModelAtlas Data](https://github.com/chenlongapps/model-atlas-data)，运行时基址为 `https://chenlongapps.github.io/model-atlas-data/`。服务商和价格继续使用 Models.dev 的 `api.json?type=all`；不再调用 Models.dev 模型端点，也不自动回退到旧模型数据。品牌素材及价格口径不变。

| 文件 | 结构与用途 |
| --- | --- |
| `models.json` | `{ "<lab>/<model-id>": { ... } }`；条目 `schema = 2`，字典键是唯一规范 ID，无内嵌 `id` |
| `benchmarks.json` | `{ "<benchmark-id>": { ...定义... } }`，不是成绩列表 |
| `i18n/zh-CN.json` | 原样读取 `model.descriptions`、`benchmark.description`，不改远程表结构 |
| `manifest.json` | `schema_version = 1`、`data_version`、`git_commit`、`published_at`、`files` 的 SHA-256 与字节数 |
| `LICENSE.models-dev` | 校验发布包上游许可与客户端已展示的随包文本一致；变化时拒绝更新，等待人工复核 |

`data/remote/atlas/` 保留独立 DTO、整包加载器与受限 HTTPS source；`AtlasCatalogMapper` 映射到本地 Entity，不向 UI 透传 DTO。能力只映射明确的 `capabilities.reasoning.supported`、`tools.supported`、`temperature`，缺失保持未知，不从服务商或模态推断附件、结构化输出等支持情况。LLM 收录规则仍按类型／输入输出模态，不因上游涵盖非 LLM 就扩大应用收录。

### 模态格式与展示类别

新数据的模态可以是 `image/png`、`audio/mp3`、`video/mp4`、`application/pdf` 等 MIME 格式，而不只是旧的类别名。DTO 和数据库保留完整原值；`ModalityCategory.kt` 在列表／详情应用模型映射时将 `text/*`、`image/*`、`audio/*`、`video/*` 和 `application/pdf` 分别投影为 `text`、`image`、`audio`、`video`、`pdf`，按首次出现顺序去重，沿用中文名称与原图标。输入、输出独立处理；未知类型保留原文，缺失与空集合仍分别为 null 与空列表，不推断附件等额外能力。

收藏复用列表映射，对比复用详情映射，筛选选项／匹配／计数使用同一类别规则。已保存的 MIME 查询与筛选草稿恢复时合并为类别，不清除搜索、机构、权重及其他有效条件。此投影对已缓存的具体格式立即生效，不要求重新下载、改数据库或清数据。真实随包 `google/gemini-2.0-flash` 的 29 个输入项按原出现顺序归为文本／图片／音频／视频／PDF 五类，原 29 项仍存储不变。

## 发布校验及同步

- 先读取清单，再下载模型、定义、中文及许可四文件；按清单校验原字节长度和摘要。清单 `files` 规范化为按键排序、UTF-8、无额外空格的 JSON 后核验 `data_version`。哈希表示相同内容，不是可排序的整数版本，允许发布方显式回滚。
- 清单上限 64 KiB，每文件上限 16 MiB，最多 100,000 个模型／成绩、10,000 个定义；下载流式限长，严格 UTF-8，仅 HTTPS，不跟随重定向，取消中止 Call。清单路径仅允许已知文件及语言 JSON，不允许远程注入绝对 URL 或父路径。
- 新模型包与 Models.dev 服务报价并行获取；全部成功后才构建候选快照。任一网络、类型、摘要、许可或数据库错误都保留原目录和成绩，不写入部分数据。429／503 的 `Retry-After` 仍走仓库共享冷却。
- `CatalogSnapshot` 包括模型、服务商、服务、成绩、定义和发布状态。模型 ID 增删仍暂存整份候选，确认后提交；ID 不变的参数／价格／成绩／翻译变化静默同步。查询及收藏不被清空。
- 原子事务同时更新目录、可用收藏身份、`benchmark_scores`、`benchmark_definitions`、`catalog_release`；详情通过同一个 `@Transaction` Relation 查询读模型、服务、成绩和定义，不用两个独立 Flow 拼接评测版本。
- `hasLocalCatalog()` 在完全没有目录且无发布标记时导入 `assets/atlas/` 的同格式真实包（无服务报价），再从 Room 立即展示并后台检查。已有目录／已提交空包不重复导入；不再在 Application 启动旧整数版本评测同步。历史 v1/v2 解析及样本只供兼容回归，不参与运行时写入。

## 成绩及本地化

- 成绩来自每个模型的 `benchmarks[]`，不从定义文件生成成绩；按模型路径＋源数组下标产生 ID，原数组顺序及不同配置记录全部保留，不选最高分。
- 成绩 `name`、`version`、`metric` 均明确时，按名称、指标及定义版本或显式 `compatible_versions` 精确查找，只有唯一候选才关联定义。缺失版本不是默认版本；歧义／未匹配使用隔离的 `unmapped:<摘要>` 身份并保留原始名称，不套用定义单位、方向和描述。
- 保留分数、来源、日期、版本、`variant`、`dataset`、`harness`；`effort` 原样映射 `reasoningEffort`，不限制枚举、不从 variant 解析或推断工具状态／来源类型。不补造 cost、单位、执行条件和可比性。
- `score` 接受有限数字或非空文本：本轮 1,837 条中 42 条含百分号、置信区间或显示限定（如 `899 ± 18`）。数字存 `score`，文本存 `scoreText` 且数值列为 null；详情原样显示文本，不提取中心值、不填零、不换算。Debug 校验至少有一种原值，禁止非有限数值。
- 模型原文保存在 `description`，中文独立保存于 `descriptionZhCn`；定义同样保留双份描述。详情优先非空白中文、缺失回退原文，不新增语言设置，不翻译名称、ID、成绩条件等原始字段。
- 评测卡在原字段前显示匹配定义的说明，保留版本、测试条件与安全来源链接，不生成排名或跨配置结论。

## 开发基线、许可与验证边界

继续遵守未发布项目的 Schema v1 基线，新增列／表直接更新导出 Schema，不启用破坏性重建、不自动清除已有开发安装数据。旧结构开发数据库可能无法打开，需开发者另行处理。收藏仍无目录外键；新发布包替换成绩，但不删除收藏。

Debug 数据库管理继续仅开放原五张业务表，补齐模型中文和成绩名称／版本／条件列；定义与发布状态由同步维护，不增加任意 SQL 或批量操作。历史 `benchmark_version` 保留供兼容测试，不参与新发布版本判断。

数据来源页标明仓库、Pages、Models.dev 价格来源及原始成绩出处；继续随包展示 Models.dev MIT 及此前 DeepSeek 文档声明。整包摘要不是数字签名，也不代表逐条成绩已被客户端人工核验。未来来源或许可变化仍须审查分发要求。

本轮随包为线上提交 `6d7c10c2e98d9537f03412bf010e63d14ad0a960` 的发布原字节；数量是快照，不是接口固定契约。实际测试与未验收范围记在 [TODO.md](../TODO.md)。
