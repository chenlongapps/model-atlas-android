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

### 模型参数量

- 仅读取 `metadata.modelatlas.parameters`，这是 ModelAtlas 扩展，不是 Models.dev 的标准参数字段。独立 DTO、本地 `ModelParametersLocal` 和应用模型 `ModelParameters` 分层映射；未知 metadata 对象字段仍忽略。
- `total_billion` 为完整模型总量，`active_billion` 为来源明确披露的每 token 激活量，均以 **B（十亿）** 为单位，必须是有限正数；同时存在时激活量不能大于总量。JSON 字符串数字、布尔、零、负数、无穷或仅有说明的空参数表均拒绝。
- 有完整总量或激活量时必须有非空、不重复的 HTTP(S) `sources`；可选 `notes` 保留原文及统计范围，不自行翻译或补充。组件各有非空名称、数值及自己的来源，同一模型内组件名称不重复。整体参数表可缺失；仅有组件时父表不必重复来源。
- 数值通过专用 JSON 数字序列化器读写为 `BigDecimal`，本地 JSON 仍为数字而非带引号文本，往返不经过 Double 舍入。显示保留有效小数、去无意义末尾零，不缩写成 T、不换算为参数个数，也不从名称、描述、权重体积、服务商数据或组件之和推算。
- `models.parameters` 为可空 JSON TEXT，包含驼峰数值字段、来源、说明及原顺序组件；只属于规范模型，不写入模型服务或收藏身份。继续参与完整目录内容比较及整包事务，参数量或口径变化可静默同步；缺失的新值会清除旧参数，而不是永久保留。
- 展示只使用总量／激活量，不从组件推算或生成参数排名；具体可见字段见[页面字段](page-information.md)，本地驼峰 JSON 见[存储契约](local-data.md#列与本地-json)。

## 发布校验及同步

- 先读取清单，再下载模型、定义、中文及许可四文件；按清单校验原字节长度和摘要。清单 `files` 规范化为按键排序、UTF-8、无额外空格的 JSON 后核验 `data_version`。哈希表示相同内容，不是可排序的整数版本，允许发布方显式回滚。
- 清单上限 64 KiB，每文件上限 16 MiB，最多 100,000 个模型／成绩、10,000 个定义；下载流式限长，严格 UTF-8，仅 HTTPS，不跟随重定向，取消中止 Call。清单路径仅允许已知文件及语言 JSON，不允许远程注入绝对 URL 或父路径。
- 新模型包与 Models.dev 服务报价并行获取；全部成功后才构建候选快照。任一网络、类型、摘要、许可或数据库错误都保留原目录和成绩，不写入部分数据。429／503 的 `Retry-After` 仍走仓库共享冷却。
- `CatalogSnapshot` 包括模型、服务商、服务、成绩、定义和发布状态。模型 ID 增删和参数／价格／成绩／翻译变化均自动提交，不再等待确认；列表展示快照与后台缓存分离，主动刷新才应用到本次列表，详见[状态流转](architecture.md#数据与状态流转)。查询及收藏不被清空。
- 原子事务同时更新目录、可用收藏身份、`benchmark_scores`、`benchmark_definitions`、`catalog_release`；详情通过同一个 `@Transaction` Relation 查询读模型、服务、成绩和定义，不用两个独立 Flow 拼接评测版本。
- `hasLocalCatalog()` 在完全没有目录且无发布标记时导入 `assets/atlas/` 的同格式真实包（无服务报价），再从 Room 立即展示并后台检查。已有目录／已提交空包不重复导入；不再在 Application 启动旧整数版本评测同步。历史 v1/v2 解析及样本只供兼容回归，不参与运行时写入。

## 成绩及本地化

- 定义可选 `popularity` 为有限 JSON 数字，缺失／null 保持未知，0 是有效值；字符串数字、错误类型或非有限值拒绝整包。经 DTO 映射保存至 `benchmark_definitions.popularity`，参与完整快照比较和同事务替换，新发布缺失时清除旧值；仅用于评测总览排序，不影响定义匹配或模型成绩排名。
- 本轮未替换随包发布字节；旧包没有热度时沿用稳定兜底顺序，既有目录同步成功后应用线上热度，不为评测页另加请求。
- 成绩来自每个模型的 `benchmarks[]`，不从定义文件生成成绩；按模型路径＋源数组下标产生 ID，原数组顺序及不同配置记录全部保留，不选最高分。
- 成绩 `name`、`metric` 必须与定义精确匹配；定义 `version = unspecified` 时不校验成绩版本，接受缺失、`unspecified` 及其他明确版本。其他定义仍要求成绩显式版本匹配定义版本或声明的 `compatible_versions`，缺失版本不是默认版本。全部候选中只有唯一匹配才关联定义，宽松定义与明确版本定义同时命中也视为歧义；歧义／未匹配使用隔离的 `unmapped:<摘要>` 身份并保留原始名称，不套用定义单位、方向和描述。放宽版本不是对原始数据集或配置可比性的核验，成绩原始版本不改写。
- 保留分数、来源、日期、版本、`variant`、`dataset`、`harness`；`effort` 原样映射 `reasoningEffort`，不限制枚举、不从 variant 解析或推断工具状态／来源类型。不补造 cost、单位、执行条件和可比性。
- `score` 接受有限数字或非空文本：当前随包 1,887 条中 31 条含百分号、置信区间或显示限定（如 `899 ± 18`）。数字存 `score`，文本存 `scoreText` 且数值列为 null；详情原样显示文本，不提取中心值、不填零、不换算。Debug 校验至少有一种原值，禁止非有限数值。
- 模型原文保存在 `description`，中文独立保存于 `descriptionZhCn`；定义同样保留双份描述。详情优先非空白中文、缺失回退原文，不新增语言设置，不翻译名称、ID、成绩条件等原始字段。
- 应用模型传递已有 `benchmarkId` 供 Logo 精确匹配，不新增存储字段、不对未知定义推断品牌。数值 UI 舍入不改原值、文本不提取数值；具体可见字段／排序见[详情字段](page-information.md#模型详情页)。

## 开发基线与兼容边界

独立评测榜单复用本地定义及成绩，不改变发布协议或详情原记录；只排名符合条件的数值，文本／未知方向／低分更优不在榜单展示。榜单投影规则见[架构说明](architecture.md#评测总览与完整榜单)，不得将其视为跨配置可比性认证。

继续遵守未发布项目的 Schema v1 基线，新增列／表直接更新导出 Schema，不启用破坏性重建、不自动清除已有开发安装数据。旧结构开发数据库可能无法打开，需开发者另行处理。收藏仍无目录外键；新发布包替换成绩，但不删除收藏。

Debug 仅开放原五表，不编辑定义／发布状态，沿用白名单与本地 JSON 校验，见[本地数据](local-data.md#debug-数据库管理)。历史整数版本／旧样本只用于[兼容回归](archive/legacy-benchmarks.md)，不参与当前发布判断。

## 许可与随包维护

数据来源页标明仓库、Pages、Models.dev 价格来源及原始成绩出处；继续随包展示 Models.dev MIT 及此前 DeepSeek 文档声明。整包摘要不是数字签名，也不代表逐条成绩已被客户端人工核验。未来来源或许可变化仍须审查分发要求。

当前随包为线上提交 `27479e48258332fe988c2d06233c86d96bef4c83` 的发布原字节：855 个模型、373 个参数表、11 个评测定义、1,887 条成绩及 852 条模型中文描述。整个 manifest、模型、定义、中文和许可一起核对摘要后更新，不单独替换模型文件。数量是快照，不是接口固定契约；实际测试与未验收范围记在 [TODO.md](../TODO.md)。
