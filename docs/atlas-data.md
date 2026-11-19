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

`data/remote/atlas/` 读取发布包，`CatalogMapper.mapAtlas()` 映射本地 Entity。能力只映射明确的 `capabilities.reasoning.supported`、`tools.supported`、`temperature`；缺失保持未知，不从服务商或模态推断其他能力。LLM [收录规则](data-source.md#收录与规范模型语义)独立于发布范围。

### 模态格式与展示类别

模态接受 `image/png`、`audio/mp3`、`video/mp4`、`application/pdf` 等 MIME。DTO／数据库保留原值；`ModalityCategory.kt` 在应用模型映射时将 `text/*`、`image/*`、`audio/*`、`video/*`、`application/pdf` 投影为对应类别，按首次出现去重、沿用中文名与图标。输入／输出独立；未知保留原文，缺失／空集仍为 null／空列表，不推断附件能力。

查询与草稿恢复也按同一类别归并；已有缓存无需重新下载。

### 模型参数量

- 仅读取 Atlas 扩展 `metadata.modelatlas.parameters`，不从名称、描述、权重体积、服务商数据或组件之和推算。
- `total_billion` 为完整模型总量，`active_billion` 为来源明确披露的每 token 激活量，均以 **B（十亿）** 为单位，必须是有限正数；同时存在时激活量不能大于总量。JSON 字符串数字、布尔、零、负数、无穷或仅有说明的空参数表均拒绝。
- 有完整总量或激活量时必须有非空、不重复的 HTTP(S) `sources`；可选 `notes` 保留原文及统计范围，不自行翻译或补充。组件各有非空名称、数值及自己的来源，同一模型内组件名称不重复。整体参数表可缺失；仅有组件时父表不必重复来源。
- 用 JSON 数字和 `BigDecimal` 往返，不经 Double 舍入。参数属于规范模型，参与快照比较；新发布缺失时清旧值。UI 只展示总量和激活量，保留 B 和有效小数。

## 发布校验及同步

- 先读取清单，再下载模型、定义、中文及许可四文件；按清单校验原字节长度和摘要。清单 `files` 规范化为按键排序、UTF-8、无额外空格的 JSON 后核验 `data_version`。哈希表示相同内容，不是可排序的整数版本，允许发布方显式回滚。
- 清单上限 64 KiB，每文件上限 16 MiB，最多 100,000 个模型／成绩、10,000 个定义；下载流式限长，严格 UTF-8，仅 HTTPS，不跟随重定向，取消中止 Call。清单路径仅允许已知文件及语言 JSON，不允许远程注入绝对 URL 或父路径。
- 与 Models.dev 报价并行获取，全部成功才[提交快照](local-data.md#快照事务与查询)。初始化与展示快照见[架构](architecture.md#数据与状态流转)。

## 成绩及本地化

- 定义的 `popularity` 为可空有限 JSON 数字，0 有效；字符串数字、其他类型和非有限值拒绝整包，新发布缺失时清旧值。只用于[总览排序](architecture.md#排名分榜与坐标)。
- 成绩来自每个模型的 `benchmarks[]`，不从定义文件生成成绩；按模型路径＋源数组下标产生 ID，原数组顺序及不同配置记录全部保留，不选最高分。
- 成绩 `name`、`metric` 必须与定义精确匹配；定义 `version = unspecified` 时不校验成绩版本，接受缺失、`unspecified` 及其他明确版本。其他定义仍要求成绩显式版本匹配定义版本或声明的 `compatible_versions`，缺失版本不是默认版本。全部候选中只有唯一匹配才关联定义，宽松定义与明确版本定义同时命中也视为歧义；歧义／未匹配使用隔离的 `unmapped:<摘要>` 身份并保留原始名称，不套用定义单位、方向和描述。放宽版本不是对原始数据集或配置可比性的核验，成绩原始版本不改写。
- 保留分数、来源、日期、版本、`variant`、`dataset`、`harness`；`effort` 原样映射 `reasoningEffort`，不限制枚举、不从 variant 解析或推断工具状态／来源类型。不补造 cost、单位、执行条件和可比性。
- `score` 接受有限数字或非空文本（如 `899 ± 18`）。数字存 `score`，文本存 `scoreText` 且数值列为 null；文本原样显示，不提取中心值、填零或换算。Debug 至少保留一种原值，禁止非有限数值。
- 模型原文保存在 `description`，中文独立保存于 `descriptionZhCn`；定义同样保留双份描述。详情优先非空白中文、缺失回退原文，不新增语言设置，不翻译名称、ID、成绩条件等原始字段。

## 许可与随包维护

随包版本以 [`assets/atlas/manifest.json`](../app/src/main/assets/atlas/manifest.json) 为准。清单、模型、定义、中文与许可一起核对摘要后更新，不单独替换文件。摘要不是数字签名或逐条人工核验；来源或许可变化须人工复核分发要求。
