# 数据源说明

## 当前状态

- [`DefaultModelRepository`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/repository/DefaultModelRepository.kt) 已通过 Retrofit / OkHttp 请求两个真实端点，后台映射后事务写入 Room3，列表由数据库 Flow 驱动，不再使用运行时示例。
- 独立 Kotlinx Serialization DTO、纯字符串入口 [`ModelsDevParser`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/remote/ModelsDevParser.kt) 与 Retrofit 共用 `ModelsDevJson`，未知字段忽略、已知字段类型严格。
- 已实现 DTO → Entity、生产数据库、DAO、完整报价存储与本地优先读取／后台检查／主动刷新策略，标识与存储契约见[本地目录数据](local-data.md)。
- 独立 `ModelSummary` 用于规范模型名称、LAB、发布时间、上下文长度、最大输出、输入／输出模态、开放权重和列表报价，不能与 DTO 或 Entity 混用，详见[架构说明](architecture.md)。

## 已核实的端点与结构

2026-10-19 直接读取以下 [Models.dev](https://models.dev/) 地址，均返回 HTTP 200 和 JSON 对象；本节结构依据实际响应，不将模型数量作为固定契约。

| 地址 | 顶层类型 | 内容 |
| --- | --- | --- |
| [`models.json?type=all`](https://models.dev/models.json?type=all) | `Map<String, ModelDto>` | 以规范模型 ID 为键，包含模型基础信息、能力、权重、链接和评测 |
| [`api.json?type=all`](https://models.dev/api.json?type=all) | `Map<String, ProviderDto>` | 以服务商 ID 为键；每个服务商的 `models` 为 `Map<String, ModelOfferingDto>`，包含该服务商下的模型服务、价格与限制 |

两个接口不是同一份数据，也不是 JSON 数组。`type=all` 的响应包含非 LLM 类型，解析层不执行收录或筛选。原规划中的 `catalog.json` 不再作为候选端点。

```kotlin
val parser = ModelsDevParser()
val models = parser.parseModels(modelsJson)
val providers = parser.parseProviders(apiJson)
```

这里的参数是已经获得的 JSON 字符串；解析入口不会自行下载数据。

## 字段与解析约定

DTO 位于 [`data/remote/dto/`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/remote/dto/)，仅表达远程结构，不定义应用模型或数据库主键。

| 主题 | 已实现的处理 |
| --- | --- |
| 标识 | 保留两份响应的顶层键、服务商模型字典键及各自的 `id`；模型服务 ID 不能假设跨服务商唯一 |
| 关联 | 仅保留显式的可选 `canonical_model_id`，不根据服务模型 ID、名称或前缀猜测对应规范模型；缺少关联的服务仍可解析 |
| 字段命名 | 使用 `@SerialName` 将 `tool_call`、`open_weights`、`release_date` 等字段映射为 Kotlin 驼峰属性 |
| 必需信息 | 模型和服务商的 `id`、`name`，以及服务商的 `models` 必须存在且非空值；空字典合法 |
| 缺失与空值 | 可选信息默认 `null`，保留缺失价格与零价、缺失能力与明确 `false`、缺失集合与空集合的区别 |
| 扩展兼容 | 忽略未知对象字段；类型、状态、模态及推理选项类型保留为字符串，模式名称保留为字典键，不使用封闭枚举 |
| 限制与日期 | 上下文、输入、输出和推理预算使用 `Long`；日期和知识截止时间保留字符串，兼容仅有年月的值 |
| 推理参数 | `reasoning_options` 包含类型、预算范围和可选值；实际响应的 `values` 中存在 `null`，使用 `List<String?>?` |
| 交错推理 | `interleaved` 使用自定义序列化器，分别映射布尔值和 `{"field":"reasoning_content"}` 对象，不接受数字或字符串布尔值 |
| 定价 | 保留输入、输出、缓存、音频、推理价格以及 `tiers` 的条件和价格、`context_over_200k`；数值使用可空 `Double`，不在 DTO 层换算或选择价格档位 |
| 服务覆盖参数 | 保留模型的服务商覆盖配置和 `experimental.modes`；自由结构的请求 `body` 使用 `JsonObject`，headers 使用字符串字典 |
| 异常 | 非法 JSON、错误的顶层结构、缺少必需字段和已知字段的非法类型抛出序列化异常，不伪造空列表或默认业务值 |

已接入请求、字段映射与存储。下文的计价、能力语义与使用条件已核实，**列表已展示单条报价**，规则见下文「报价选取与展示」。`Double` 仅用于目录数据解析、存储和展示格式化，不用于财务结算。

### 已实现的列表收录与聚合

- 一行表示 `models.json` 中一个规范模型，稳定 ID 使用字典键，不按名称合并。
- 明确 `type = "llm"` 时收录；`type` 缺失时，仅在 `modalities.input` 与 `modalities.output` 均包含 `text` 时收录，允许额外图片、音频等模态。
- 明确非 LLM、未知类型、类型为空字符串、类型缺失且模态不足的记录不收录。仅音频输入的转录、纯图像或视频生成等不会因为输出文字或输入提示词而自动纳入；规则不是能力认证。
- 只通过服务的显式 `canonical_model_id` 关联 API 服务商；列表映射按规范模型 ID 去重，不再展示 API 服务商和摘要，不从服务名称或 ID 反推规范模型。
- 缺少关联时仍保留规范模型。服务侧缺失能力与限制不由规范模型覆盖；未匹配服务仍独立存储，不作为额外规范模型展示。
- 列表的 LAB 按界面约定取稳定 `model.id`（规范模型字典键）第一个 `/` 前的非空白部分；无有效前缀时显示“暂无数据”。这是 ID 前缀标签，不是 API 服务商或独立核实的模型开发商。该前缀同时决定列表显示的 LAB logo：随包素材取自 Models.dev 的 Logos 服务（`/logos/labs/{lab}.svg` 同图文件的本地彩色变体），只覆盖 23 个实验室，按前缀精确匹配；没有素材时显示兜底图，不借用其他实验室素材，也不把 logo 当作开发商认定。
- 名称为空白时用该记录稳定 ID 展示；发布时间取规范模型 `release_date`，保留年月等来源精度，空白视为缺失。上下文长度与最大输出分别取规范模型 `limit.context` / `limit.output`，原值保留为 `Long`，列表使用十进制 K／M 缩写，最多一位小数并去除末尾零（如 `1M`、`384K`、`512K`、`131.1K`）；读屏仍提供字段名、带千位分隔的精确值与 tokens 单位，不聚合服务商限制。
- 多模态能力取规范模型 `modalities.input` / `modalities.output`，分别显示输入、输出；已知类型显示中文，未知类型保留原值。缺失字段显示“暂无数据”，显式空集合显示“无”，不拼造能力描述。
- 开放权重取规范模型 `open_weights` 三态：`true` 显示“开放权重”，`false` 显示“权重未公开”，缺失显示“开放权重：暂无数据”。仅在 `true` 且 `license` 去除首尾空白后为 1–16 个字符时，以“开放权重 · MIT”这样的中点形式附带原始许可短名；长名称不截断或推测简称，列表仅显示“开放权重”。完整许可始终保留在应用模型和本地目录中，供未来详情页展示（尚未实现）。这是来源报告的权重可获取状态，不等于开源或允许商用，界面来源说明已注明，不替代核对模型许可。
- 报价取显式关联的模型服务，按「报价选取与展示」的规则每个规范模型展示一条；同一服务商多个模型服务取输入价最低的一条。界面不显示服务商名称，只显示价格类别与数值；缺失或不可用报价显示「价格：暂无数据」，明确的 `0` 显示为 `$0.00`。

## 已核实的计价约定

以下语义与使用条件于 2026-10-19 对照上游文档、Schema、生成代码及服务商官方定价核实，证据链接见文末。它们是数据源约定和后续接入规则，不表示已逐一验证目录中的所有模型与报价。

### 币种与数量单位

- 上游要求所有 `cost` 数值统一为 **美元（USD）/ 百万（1,000,000）tokens**，包括 `tiers` 与 `experimental.modes` 内的价格；不是每次请求、每千 tokens 或每百万字符的价格。
- 非美元报价由上游换算为 USD，并要求在源 TOML 的文件头注释中记录汇率及日期。这些注释不包含在 JSON 中；目录币种不代表服务商实际结算币种，也不应再次按服务商所在地或名称推断汇率。

| 字段 | 数量口径 |
| --- | --- |
| `cost.input` | 百万输入 tokens |
| `cost.output` | 百万输出 tokens |
| `cost.cache_read` | 百万缓存读取 tokens，仅适用于符合缓存命中条件的部分 |
| `cost.cache_write` | 百万缓存写入 tokens，不表示任意缓存时长或存储费用均已包含 |
| `cost.input_audio` / `cost.output_audio` | 单独计费时的百万音频输入 / 输出 tokens，不是音频分钟数 |
| `cost.reasoning` | 百万推理 tokens，不能据此认定它一定是在输出费之外另收的费用 |

该口径用于首版 LLM 报价；不能据此把非 LLM 的图片张数、视频秒数、音频时长等计费方式转换为可直接比较的 token 价格。

## 报价选取与展示

列表每个规范模型只展示**一条**报价，因为同一规范模型在 `api.json` 中可有多个服务商的模型服务，各自报价与限制不同，不能合并成一个无条件通用价格。

- 只使用通过显式 `canonical_model_id` 关联到该规范模型的模型服务，不按名称、服务商 ID 或 LAB 前缀猜测对应关系。
- 官方优先：`PriceProviderPolicy` 内置 LAB → 第一方服务商的对应表，表中的服务商只要有可用基础报价就采用。该表于 2026-10-19 对照上游 `api.json` 核实，仅收录确有第一方服务且通过显式关联提供报价的条目；未列入的 LAB 一律没有官方报价，不把 LAB 本身当作官方认定。
- 没有官方报价时按第三方优先级选取，顺序为 OpenRouter、Together AI、Fireworks AI、Deep Infra、SiliconFlow、Novita AI、Hugging Face、Amazon Bedrock、Google Vertex、Azure；未列入者排在其后，再按服务商 ID 稳定排序。这是应用内的展示顺序，不是质量、速度、可用性或最低价排行榜。
- 同一服务商对同一规范模型有多个模型服务时，取输入价最低的一条（其次输出价最低，最后按服务内稳定键排序），避免把高速或预览变体当作该服务商的代表报价。
- “可用基础报价”指输入或输出单价非空，或某个 `tiers` 档位提供了其中之一；明确的 `0` 是可用报价。`cost` 整体为 `null`、字段全为 `null` 或只有 `context_over_200k` 等兼容字段时不展示价格，界面显示“暂无数据”。
- 卡片以「API 价格 · 美元 / 1M tokens」为小节标题，在能力区下方用一条细分隔线分区，不再嵌套独立底色或圆角价格卡片；分列展示输入、输出和存在时的缓存命中输入单价，逐行展示来源提供的缓存写入、推理、音频输入、音频输出单价。不展示服务商名称，也不标注“官方／第三方”或来源未提供的“非高峰”。`tiers` 的上下文档位默认折叠，有档位时点击标题行及其箭头展开；档位门槛始终使用完整数值并增加千位分隔，不随列表限制缩写为 K／M，以免改变计价条件。
- 展示不选择档位、不计算总价或最低价，不把 `output` 与 `reasoning` 相加，也不把 `experimental.modes` 的模式价与基础价混用；`context_over_200k` 只是旧版兼容信息，不单独展示为档位。
- 价格格式化至少保留两位小数（`0.6` → `$0.60`），来源更精确时保留更多位（`0.003` → `$0.003`）；`Double` 只用于展示格式化，不用于财务结算。
- 列表底部与“数据来源与许可”弹窗都注明：同一模型的不同服务商价格不可直接比较，缓存、档位、模式与订阅等适用条件以服务商说明为准。

### 报价范围与适用条件

- **价格属于具体服务商的模型服务。** `models.json` 描述规范模型，`api.json` 的模型服务描述该服务商的报价与限制；同一规范模型不能合并成一个无条件通用价格。
- `cost` 或其中字段缺失 / 为 `null` 表示暂无数据，不等于零；明确的 `0` 只表示该报价字段为零，不能推断整个服务永久免费、无需订阅或没有配额限制。
- `cost.tiers[].tier.type = "context"` 表示按上下文条件分档，`size` 为以 tokens 表示的档位门槛，不是模型最大上下文或单次数量价格。门槛等号归属、输入 / 缓存 tokens 的计入方式及整次请求如何适用，应以对应服务商说明为准；不能自行假设按超出部分累进收费。
- **精确门槛以 `cost.tiers` 为准。** `context_over_200k` 是旧版输出兼容字段：上游在仅有一个上下文档位且门槛不小于 200,000 时也会生成它。核实时 OpenAI 的 `gpt-5.4` 门槛为 **272,000**，仍带有这个旧字段；不能按字段名认定为 200,000，也不能将它与 `tiers` 重复计算。只有旧字段而没有精确档位时，不猜测适用门槛。
- `experimental.modes` 描述特定模式的价格与请求覆盖参数，例如快速模式的 `body` / headers；不能把它与基础模式价格混用，模式之间的覆盖与组合条件仍应核对服务商文档。
- 目录未完整表达缓存 TTL、缓存存储费、地区、批处理、订阅、配额、促销有效期、工具调用附加费等条件。例如 Claude 的缓存写入价区分 5 分钟与 1 小时，Gemini 的显式缓存还可能收取存储费；不能从一个 `cache_write` 或 `cache_read` 数值推断完整费用。
- 推理 tokens 可能已计入输出计费，缺失 `cost.reasoning` 不代表推理免费；没有明确计费说明时，不将 `output` 与 `reasoning` 简单相加。相同文本在不同模型上的 token 数也可能不同，同单位单价不等于同任务实际成本。

列表展示遵循上述约定：分别注明输入 / 输出 / 缓存 / 推理 / 音频等类别、基础价与档位，并在小节标题注明 **USD / 百万 tokens**；不展示服务商名称，因此同时保留“不可直接比较”提示。展示不生成无条件最低价、总费用或账单承诺；实际费用与可用性以服务商最新说明为准。

## 能力与限制语义

`models.json` 的能力是模型本身的元数据；`api.json` 的能力是生成时继承并允许服务商覆盖后的服务信息。后续规范模型详情与服务商服务详情应分别使用对应记录，不能用规范模型的值覆盖服务商限制，也不能把多个服务商能力取并集后宣称每家都支持。

| 字段 | 已核实含义与使用边界 |
| --- | --- |
| `attachment` | 支持文件附件；不承诺接受任意文件格式，具体输入类型还需查看 `modalities.input` 与服务商文档 |
| `modalities.input` / `modalities.output` | 支持的输入 / 输出模态，方向不可混用；接收图片不等于能生成图片 |
| `reasoning` | 支持推理能力；不是质量评分，不保证公开完整思维链，也不表示允许开关或调节强度 |
| `reasoning_options` | 该服务商的推理控制方式：`toggle` 为开关，`effort` 为强度选项，`budget_tokens` 为推理 token 预算；空列表表示没有调用方控制，缺失表示未知。选项中的 `null` 保留原值，不擅自解释为关闭 |
| `interleaved` | 交错推理的支持 / 格式信息；对象的 `field` 指明该 API 的推理通道字段，不是通用推理开关 |
| `tool_call` | 支持工具调用；不代表服务自带工具执行、联网搜索或免费工具额度 |
| `structured_output` | 支持专门的结构化输出功能，不等同于仅靠提示词要求 JSON；支持的 Schema 与组合限制取决于服务商 |
| `temperature` | 支持温度调节，不是默认温度值，也不保证在所有推理模式下都可调 |
| `open_weights` | 训练权重公开可获取；不等于开源、允许任意用途或允许商用，须查看模型的 `license` 与许可链接 |
| `limit.context` / `limit.input` / `limit.output` | 最大上下文、输入与输出的 token 限制，不是 RPM / TPM 等请求额度；不能自行用上下文减输出推导缺失的输入限制 |
| `status` | 该服务商上的 `alpha` / `beta` / `deprecated` 生命周期标记，不代表所有服务商上的同一模型均处于相同状态 |

所有能力展示保留三态：`true` 为“支持”、`false` 为“不支持”、缺失 / `null` 为“暂无数据”。缺失集合不能当成空集合；未知选项与模态不强行归为“不支持”。这些是来源报告的能力，不是本应用已执行验证的结果，也不保证多项能力可在同一次请求中组合使用。

## 接口与数据使用条件

### 请求、缓存与时效

- 本次读取两个公开 JSON 端点无需 API Key；服务商记录中的 `env` 是调用该服务商模型 API 的配置，不是读取 Models.dev 目录的鉴权要求。
- 所查上游 README 与公开 API 说明未公布固定 QPS 配额或 SLA，不能理解为允许无限请求或保证可用。当前已设置超时、控制重试，并在限流时尊重 `Retry-After`；进入页面检查一次，主动刷新防重入，不高频轮询。
- 核实时两个端点均返回 `Cache-Control: public, max-age=0, must-revalidate` 和 `ETag`。这是当次 HTTP 观察，不是固定缓存契约；后续 HTTP 缓存应遵循实际响应头，不把它与推理 API 的 prompt cache 混淆，也不据此承诺固定刷新周期。
- 数据由社区维护，不保证与服务商实时同步。远程 `last_updated` 是条目元数据，不是本地抓取时间，也不能当作价格生效时间；当前映射已单独记录来源与获取时间。

### 数据许可与来源署名

- 上游仓库采用 **MIT License**，版权声明为 `Copyright (c) 2025 models.dev`，允许使用、修改、分发及商业使用，并按原样提供、无担保。所查仓库未声明另一份独立的数据许可。
- 分发上游数据或其重大部分时，应保留版权声明及完整 MIT 许可文本（含免责条款）；**仅写来源名称或放链接不能替代许可声明**。全文已随 `res/raw/models_dev_license.txt` 打包，可通过列表右上角“数据来源与许可”查看。
- 来源说明继续注明 Models.dev，并说明本应用为独立第三方；这与保留许可文本是不同事项。上游 MIT 不自动决定本项目自身的许可证，也不替代各模型权重许可、服务商条款或商标使用规则。
- 列表的 LAB 彩色 logo 同样来自 Models.dev 的 Logos 服务，已转换为 Android 矢量资源随 `app/src/main/res/drawable/ic_lab_*.xml` 打包，不运行时请求；logo 为品牌商标，仅用于标示目录中的 LAB 前缀，不代表隶属、赞助或认可。

## 核实依据

上游仓库资料固定到核实时的提交 `00b5db7ae590a0e3cbafb2d64df4da111c49c1e7`，便于追溯；在线报价与服务条件可能随后变化。

- [上游 README](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/README.md)：计价单位、能力字段、模型与服务商覆盖关系。
- [上游维护规范](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/AGENTS.md)：统一 USD、汇率注释、上下文档位及推理选项语义。
- [字段 Schema](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/packages/core/src/schema.ts)与[生成代码](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/packages/core/src/generate.ts)：价格字段、档位与旧字段兼容逻辑。
- [上游 LICENSE](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/LICENSE)：MIT 授权、版权保留及免责条款。
- [OpenAI 定价](https://developers.openai.com/api/docs/pricing)、[Claude 定价](https://platform.claude.com/docs/en/about-claude/pricing)、[Gemini 定价](https://ai.google.dev/gemini-api/docs/pricing)：核对上下文门槛、缓存及模式等条件的官方示例，不作为全部服务商的统一规则。

## 测试与后续接入

- 解析测试使用 [`app/src/test/resources/modelsdev/`](../app/src/test/resources/modelsdev/) 中的合成样本及测试内的边界 JSON，无需联网。样本说明记录了结构依据与核实日期，不将合成数值当作真实报价。
- 已覆盖两类完整嵌套结构、跨服务商重复模型 ID、缺失与显式空值、未知字段、动态模式、整数与小数价格、超出 Int 的限制值、`interleaved` 双向序列化和异常响应。
- 已添加普通 `INTERNET` 权限、客户端、Hilt 配置、后台映射、异步加载与失败重试。客户端设置连接 15 秒、读取 30 秒、整次调用 60 秒超时，不自动重试或轮询。HTTP 429 / 503 尊重 `Retry-After` 秒数或 HTTP 日期；缺失或无效时冷却 60 秒，冷却期间用户重试不会再次请求服务器。
- 仅在两个端点均成功获取、解析和映射后形成完整快照；本地获取时间按各响应成功后的时钟记录，同一响应记录保持一致。网络失败、非法响应或取消不写入部分目录。新的列表 ViewModel 优先读取并观察本地目录；有任一目录记录时后台只检查、提示更新，用户确认后才事务提交暂存快照；没有记录时自动获取并提交。下拉刷新主动获取并提交，检查期间下拉复用该请求。后台检查失败不打扰本地浏览，主动刷新失败显示非阻断提示，不清空本地目录。
- 变化比较涵盖两端点映射后的完整模型、服务商和模型服务内容（含报价），以稳定主键匹配，忽略本地获取时间与字典顺序；仅抓取时间改变不会提示更新或重复写库。待应用快照仅存内存，进程重启后重新检查，不持久化另一份目录。有效空快照仍可经主动刷新／确认清空旧目录。
- 网络路径与解析由 MockWebServer 测试覆盖，仓库和 ViewModel 异步行为使用 fake 与测试调度器；实际 SQLite 事务与列表状态交互由 `androidTest` 验证。
- 展示时遵循上文已核实的口径；具体服务商的档位边界、缓存条件、模式组合及调用权限等未完整表达的信息，仍需按官方说明核实，不补造默认值。数据来源、独立第三方与 MIT 许可说明已落实。
- 已补充本地优先、更新检查与提交分离、主动刷新、失败保留数据及并发操作测试；更新时间展示尚未实现。接入和缓存任务的完成状态只在 [TODO.md](../TODO.md) 维护。
