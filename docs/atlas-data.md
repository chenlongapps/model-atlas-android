# ModelAtlas Data 接入契约

## 当前实现

模型、评测成绩／定义和中文来自 [ModelAtlas Data](https://github.com/chenlongapps/model-atlas-data)，基址为 `https://chenlongapps.github.io/model-atlas-data/`。服务商和价格见[Models.dev 契约](data-source.md)，不回退旧模型端点。

| 文件 | 结构与用途 |
| --- | --- |
| `models.json` | `{ "<lab>/<model-id>": { ... } }`；条目 `schema = 2`，字典键是唯一规范 ID，无内嵌 `id` |
| `benchmarks.json` | `{ "<benchmark-id>": { ...定义... } }`，不是成绩列表 |
| `i18n/zh-CN.json` | 原样读取 `model.descriptions`、`benchmark.description`，不改远程表结构 |
| `manifest.json` | `schema_version = 1`、`data_version`、`git_commit`、`published_at`、`files` 的 SHA-256 与字节数 |
| `LICENSE.models-dev` | 校验发布包上游许可与客户端已展示的随包文本一致；变化时拒绝更新，等待人工复核 |

`data/remote/atlas/` 保留独立 DTO、整包加载器与受限 HTTPS source；`AtlasCatalogMapper` 映射到本地 Entity，不向 UI 透传 DTO。能力只映射明确的 `capabilities.reasoning.supported`、`tools.supported`、`temperature`，缺失保持未知，不从服务商或模态推断附件、结构化输出等支持情况。LLM 收录规则仍按类型／输入输出模态，不因上游涵盖非 LLM 就扩大应用收录。

### 模态格式与展示类别

模态接受 `image/png`、`audio/mp3`、`video/mp4`、`application/pdf` 等 MIME。DTO／数据库保留原值；`ModalityCategory.kt` 在应用模型映射时将 `text/*`、`image/*`、`audio/*`、`video/*`、`application/pdf` 投影为对应类别，按首次出现去重、沿用中文名与图标。输入／输出独立；未知保留原文，缺失／空集仍为 null／空列表，不推断附件能力。

收藏／对比复用列表／详情映射，筛选选项、匹配、计数同规则。保存的 MIME 查询及草稿恢复时合并类别，保留其他有效条件；缓存立即生效，不需下载、改库或清数据。

### 模型参数量

- 仅读取 `metadata.modelatlas.parameters`，这是 ModelAtlas 扩展，不是 Models.dev 的标准参数字段。独立 DTO、本地 `ModelParametersLocal` 和应用模型 `ModelParameters` 分层映射；未知 metadata 对象字段仍忽略。
- `total_billion` 为完整模型总量，`active_billion` 为来源明确披露的每 token 激活量，均以 **B（十亿）** 为单位，必须是有限正数；同时存在时激活量不能大于总量。JSON 字符串数字、布尔、零、负数、无穷或仅有说明的空参数表均拒绝。
- 有完整总量或激活量时必须有非空、不重复的 HTTP(S) `sources`；可选 `notes` 保留原文及统计范围，不自行翻译或补充。组件各有非空名称、数值及自己的来源，同一模型内组件名称不重复。整体参数表可缺失；仅有组件时父表不必重复来源。
- 数值通过专用 JSON 数字序列化器读写为 `BigDecimal`，本地 JSON 仍为数字而非带引号文本，往返不经过 Double 舍入。显示保留有效小数、去无意义末尾零，不缩写成 T、不换算为参数个数，也不从名称、描述、权重体积、服务商数据或组件之和推算。
- 仅属于规范模型，参与完整快照比较／事务；数值或口径变化可同步，新发布缺失须清旧值。存储格式见[本地 JSON](local-data.md#列与本地-json)。
- UI 仅用总量／激活量，不从组件推算或生成排名；可见字段见[页面字段](page-information.md)。

## 发布校验及同步

- 先读取清单，再下载模型、定义、中文及许可四文件；按清单校验原字节长度和摘要。清单 `files` 规范化为按键排序、UTF-8、无额外空格的 JSON 后核验 `data_version`。哈希表示相同内容，不是可排序的整数版本，允许发布方显式回滚。
- 清单上限 64 KiB，每文件上限 16 MiB，最多 100,000 个模型／成绩、10,000 个定义；下载流式限长，严格 UTF-8，仅 HTTPS，不跟随重定向，取消中止 Call。清单路径仅允许已知文件及语言 JSON，不允许远程注入绝对 URL 或父路径。
- 与 Models.dev 报价并行获取，全部成功才构建完整 `CatalogSnapshot`；网络、类型、摘要、许可、数据库错误或取消均不写半份。429／503 共用仓库 `Retry-After` 冷却。
- 目录、成绩、定义、发布状态及可用收藏身份[同事务提交](local-data.md#快照事务与查询)，查询和收藏不清空；增删或内容变化自动提交，无确认步骤。[列表展示快照](architecture.md#缓存与列表展示快照)与后台缓存分开。
- 完全无目录且无发布标记时，`hasLocalCatalog()` 导入同格式 `assets/atlas/` 真包（无报价），展示后后台检查；已有目录或已提交空包不重复导入。

## 成绩及本地化

- 定义可选 `popularity` 为有限 JSON 数字，缺失／null 保持未知，0 是有效值；字符串数字、错误类型或非有限值拒绝整包。经 DTO 映射保存至 `benchmark_definitions.popularity`，参与完整快照比较和同事务替换，新发布缺失时清除旧值；仅用于评测总览排序，不影响定义匹配或模型成绩排名。
- 随包无热度时使用稳定兜底顺序，目录同步后应用线上值，不为评测页另加请求。
- 成绩来自每个模型的 `benchmarks[]`，不从定义文件生成成绩；按模型路径＋源数组下标产生 ID，原数组顺序及不同配置记录全部保留，不选最高分。
- 成绩 `name`、`metric` 必须与定义精确匹配；定义 `version = unspecified` 时不校验成绩版本，接受缺失、`unspecified` 及其他明确版本。其他定义仍要求成绩显式版本匹配定义版本或声明的 `compatible_versions`，缺失版本不是默认版本。全部候选中只有唯一匹配才关联定义，宽松定义与明确版本定义同时命中也视为歧义；歧义／未匹配使用隔离的 `unmapped:<摘要>` 身份并保留原始名称，不套用定义单位、方向和描述。放宽版本不是对原始数据集或配置可比性的核验，成绩原始版本不改写。
- 保留分数、来源、日期、版本、`variant`、`dataset`、`harness`；`effort` 原样映射 `reasoningEffort`，不限制枚举、不从 variant 解析或推断工具状态／来源类型。不补造 cost、单位、执行条件和可比性。
- `score` 接受有限数字或非空文本（如 `899 ± 18`）。数字存 `score`，文本存 `scoreText` 且数值列为 null；文本原样显示，不提取中心值、填零或换算。Debug 至少保留一种原值，禁止非有限数值。
- 模型原文保存在 `description`，中文独立保存于 `descriptionZhCn`；定义同样保留双份描述。详情优先非空白中文、缺失回退原文，不新增语言设置，不翻译名称、ID、成绩条件等原始字段。
- 应用模型传递已有 `benchmarkId` 供 Logo 精确匹配，不新增存储字段、不对未知定义推断品牌。数值 UI 舍入不改原值、文本不提取数值；具体可见字段／排序见[详情字段](page-information.md#模型详情页)。

## 开发基线与兼容边界

- 榜单复用本地定义／成绩，不改变协议或详情原记录，不是跨配置可比性认证；投影见[架构](architecture.md#排名分榜与坐标)。
- 沿用[Schema v1 开发基线](local-data.md#表与开发基线)，不自动迁移、重建或清数据；Debug 只编辑[原五表](local-data.md#debug-数据库管理)。
- 支持的远程协议见[数据源边界](data-source.md#当前来源与边界)，不留旧协议兼容代码或样本。

## 许可与随包维护

来源页标明仓库、Pages、Models.dev 价格与原始成绩出处，离线展示 Models.dev MIT。整包摘要不是数字签名或逐条人工核验；来源／许可变化须审查分发要求。

当前随包为线上提交 `27479e48258332fe988c2d06233c86d96bef4c83` 的发布原字节：855 个模型、373 个参数表、11 个评测定义、1,887 条成绩及 852 条模型中文描述。整个 manifest、模型、定义、中文和许可一起核对摘要后更新，不单独替换模型文件。数量是快照，不是接口固定契约；实际测试与未验收范围记在 [TODO.md](../TODO.md)。
