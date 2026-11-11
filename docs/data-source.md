# 数据源说明

## 当前来源与边界

| 内容 | 当前来源 | 契约 |
| --- | --- | --- |
| 规范模型、评测成绩／定义、中文 | ModelAtlas Data Pages | [Atlas 发布包](atlas-data.md) |
| 服务商、模型服务、价格／服务限制 | Models.dev `api.json?type=all` | 本文 |
| 首次离线模型／评测／翻译 | 同协议 `assets/atlas/` 真实包，无价格 | [Atlas 初始化](atlas-data.md#发布校验及同步) |

仅支持 Atlas 整包与 Models.dev 服务商端点；旧整数版本评测同步、Models.dev `models.json?type=all` 解析入口及对应测试样本已删除，不再兼容这些历史协议。不能把当前 `benchmarks.json` 定义字典当旧成绩快照。

规范模型、具体 API 服务不是同一对象，只按服务显式 `canonical_model_id` 关联，不以名称、ID 相似度或 LAB 猜测。参数／能力只取对应层，不用规范模型覆盖服务缺失值，不将服务能力取并集。存储标识见[本地数据](local-data.md)，展示字段见[页面字段](page-information.md)。

## 请求与解析

- 当前 `api.json?type=all` 顶层为 `Map<String, ProviderDto>`，服务商下 `models` 为服务字典；不是数组。读取公开目录不需 API Key，`env` 是调用模型服务的环境变量名称，不是目录鉴权。
- `ModelsDevApi` 负责 Retrofit 请求，`ModelsDevParser.parseProviders(apiJson)` 解析已有字符串，不自行下载，共用 `ModelsDevJson`。`ModelDto` 仅为 Atlas 映射复用的规范字段载体，不是独立远程解析入口。
- DTO 在 `data/remote/dto/`，必需 `id`／`name`、服务商 `models` 不可缺失或 null，空字典合法；顶层键、服务内键、条目内 ID 分开保留。
- `@SerialName` 映射蛇形字段；可选字段默认 null，区分缺失／明确 false／零／空集合。忽略未知对象字段，类型／状态／模态／模式键保持开放字符串。
- token 限制与预算使用 Long，日期保持来源字符串及精度；`reasoning_options.values` 保留 null。`interleaved` 只接受布尔或 `{"field":"reasoning_content"}`，拒绝字符串布尔或数值。
- 完整保留七类价格、`tiers`、旧 `context_over_200k`、服务覆盖与 `experimental.modes`。价格 Double 仅用于解析／存储／展示，不是财务结算类型；自由请求 `body`／headers 只存储，不展示或执行。
- 非法 JSON、顶层错误、缺必需值、已知字段类型错误抛出异常，不伪造成空目录或默认业务值。
- 客户端连接 15s、读取 30s、整次 60s，不自动重试。429／503 尊重 `Retry-After` 秒数或 HTTP 日期，缺失／非法则冷却 60s，冷却中重试不发请求；不轮询、不增加退出后后台任务。
- Atlas 整包及价格响应均成功才事务提交，网络／解析／取消不写半份。比较／后台确认／主动刷新策略只在[架构](architecture.md#数据与状态流转)维护。

### 收录与规范模型语义

- 每个规范 ID 一行，同名不合并，无服务仍展示；明确 `type = "llm"` 收录，类型缺失时须输入／输出均含 `text`。明确非 LLM、未知／空类型或模态不足不收录，不是能力认证。
- LAB 仅为规范 ID 第一个 `/` 前非空前缀，无有效前缀为未知；不是 API 服务商或独立核实的开发商。品牌显示名与 Logo 不改变底层 ID、搜索或关联规则。
- 发布日期保留来源精度，映射按日期字符串倒序，缺失最后，同日按名称（忽略大小写）和 ID 稳定排序；不补日期，也不聚合服务限制。
- 规范能力与参数格式由 [Atlas 契约](atlas-data.md)决定，不再将旧 Models.dev 模型字段当生产来源。模态方向、未知／空集合分开，MIME 类别投影不改原存储。

## 已核实的计价约定

以下于 2026-10-19 对照上游及官方例子核实，是来源口径，不表示逐项验证过全部实时报价。

所有 `cost`（含 `tiers`／模式）统一为 **USD / 1,000,000 tokens**，不是每次请求、每千 tokens 或字符。非美元由上游换算，汇率／日期写在源 TOML 注释、不在 JSON 中；不据服务商地区推断结算币种或再次换汇。

| 字段 | 数量口径／限制 |
| --- | --- |
| `input` / `output` | 百万输入／输出 tokens |
| `cache_read` / `cache_write` | 百万缓存读取／写入 tokens，不承诺任意 TTL 或存储费全包含 |
| `input_audio` / `output_audio` | 单独计费的百万音频 tokens，不是分钟数 |
| `reasoning` | 百万推理 tokens，不保证在输出费之外另收 |

非 LLM 的图片张数、视频秒数、音频时长不能按此换成可比 token 价格。

## 报价选取与展示

列表及对比的默认报价只取一条显式关联服务，不拼成无条件通用价。`PriceProviderPolicy` 与 `selectPriceFor` 负责选择：

1. LAB→第一方对应表中有可用报价则优先；没列入不将 LAB 自身当官方认定。
2. 否则按第三方顺序：OpenRouter、Together AI、Fireworks AI、Deep Infra、SiliconFlow、Novita AI、Hugging Face、Amazon Bedrock、Google Vertex、Azure；其余按服务商 ID。这不是质量、速度、可用性或最低价排名。
3. 同服务商多服务按输入价最低、其次输出价、最后服务稳定键选取，不把不同服务参数拼接。

“可用”是基础输入／输出或某个 `tiers` 提供其一，0 也有效；只有旧 `context_over_200k`、全空 cost 或整体 null 不展示报价。价格至少两位小数，来源更精确则保留更多（如 `$0.003`），不舍成零。

| 页面 | 报价范围 |
| --- | --- |
| 列表 | 默认一条；基础七类单价及精确档位，不显示服务商 |
| 对比 | 沿用列表同一条，标明服务商／服务 ID，七类单价及档位，不切服务 |
| 详情 | 全部显式关联服务，含无价；第一方／第三方优先及 ID 兜底，同服务商按服务 ID，不筛最低；每条仅服务商、服务 ID、服务上下文／最大输出、基础输入／输出价 |

详情缺失不从规范模型、别的服务、档位或独立模式补齐；完整报价仍在库／应用模型中。可见字段与折叠归[页面字段](page-information.md)，外观归设计文档。

### 报价范围与适用条件

- 价格属于具体服务。null 是未知，明确 0 仅该字段为零，不代表永久免费、无需订阅或无额度。
- `tiers[].tier.type = "context"` 的 `size` 是精确 token 条件门槛，不是模型上下文或单次数量价格；等号归属、输入／缓存计入方式及整次请求如何应用以服务商为准，不假设超额累进计费。
- `context_over_200k` 只是旧兼容字段；仅有一个不小于 200,000 的档位时也可能生成。核实时 OpenAI `gpt-5.4` 精确门槛为 272,000，不能照字段名当 200,000 或与 tiers 重算；无精确档位不猜门槛。
- 模式价／请求覆盖与基础价分开，不混模式、不推断组合条件；展示不选适用档位、不计算总价或“全网最低”。
- 缓存 TTL／存储费、地区、批处理、订阅、额度、促销、工具附加费未完整表达；Claude 写缓存时长、Gemini 显式缓存存储费等须看官方条款。
- 推理可能已计入输出，缺失 reasoning 不代表免费，不把 output 与 reasoning 简单相加；不同模型相同文本 token 数也不同，同单位不等于同任务成本。
- 来源页集中注明价格适用条件与不同服务商不可直接比较，实际费用／可用性以官方最新说明为准，不作账单承诺。

## 能力与限制语义

| 字段 | 含义与不能推断的内容 |
| --- | --- |
| `attachment` | 文件附件，不承诺任意格式 |
| `modalities.input` / `output` | 输入／输出独立，能看图不等于能生成图 |
| `reasoning` | 推理支持，不是质量分、不保证公开思维链或可调强度 |
| `reasoning_options` | toggle／effort／budget_tokens 控制；空列表无调用方控制、缺失未知，选项 null 不猜关闭 |
| `interleaved` | 布尔支持或 API 推理通道 field，不是通用开关 |
| `tool_call` | 工具调用，不等于自带执行、搜索或免费额度 |
| `structured_output` | 专门结构化功能，不等于提示词要求 JSON；Schema／组合限制另核 |
| `temperature` | 支持调温，不是默认温度，也不承诺所有模式可用 |
| `open_weights` | 权重可获取，不等于开源／商用授权 |
| `limit.context` / `input` / `output` | token 上限，不是 RPM／TPM，不能相减推算缺失值 |
| `status` | 服务侧 alpha／beta／deprecated，不代表所有服务商上的模型状态 |

true／false／null 为支持／不支持／未知，缺失集合不是空集合。这些是来源报告而非本应用执行验证，不保证多能力可同时组合。参数量仅读 Atlas 扩展，不从名称、服务或组件之和补齐，见[参数契约](atlas-data.md#模型参数量)。

## 数据许可与来源署名

- Models.dev 仓库为 MIT，`Copyright (c) 2025 models.dev`；分发重大部分须保留完整版权及免责文本，仅名称／链接不替代许可。`res/raw/models_dev_license.txt` 随包，设置来源子页离线可读。
- 独立第三方说明与许可保留是两件事；MIT 不决定本项目许可证、不替代权重／服务／商标条款。Atlas 许可与随包维护见[接入契约](atlas-data.md#许可与随包维护)。
- LAB 素材来自 Models.dev Logos（`/logos/labs/{lab}.svg` 同图本地彩色变体），随包 `ic_lab_*.xml`，覆盖 23 个前缀；不运行时下载、不借图、不代表隶属／认可。
- 服务商源为 `https://models.dev/logos/{provider-ID}.svg`；2026-10-27 的 232 ID 快照收录 212 ID／187 份矢量，只有原 SVG 完全相同才共用。12 个通用图 ID：`alibaba-token-plan-cn`、`chutes`、`cline-pass`、`cortecs`、`gmicloud`、`morph`、`requesty`、`sakana`、`sap-ai-core`、`sarvam`、`synthetic`、`upstage`；8 个无法完整转换 ID：`abliteration-ai`、`atomic-chat`、`hpc-ai`、`kenari`、`orcarouter`、`umans-ai`、`umans-ai-coding-plan`、`wafer.ai`。这些及未知 ID 都用独立兜底，数量不是接口保证。
- 服务商 SVG 用构建期 Svg2Vector 转换，保留 viewBox／变换／显式颜色，固有 24dp、等比绘制，`currentColor` 固定黑配白底。`lucidquery` 超长路径先 SVGO 优化，不拆复合路径或换品牌；资源注释留地址／日期，更新须验证全部加载与图形，不要求运行时工具。

## 核实依据与时效

上游固定提交 `00b5db7ae590a0e3cbafb2d64df4da111c49c1e7`：
- [README](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/README.md)、[维护规范](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/AGENTS.md)：币种、单位、覆盖及推理控制。
- [Schema](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/packages/core/src/schema.ts)、[生成代码](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/packages/core/src/generate.ts)、[LICENSE](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/LICENSE)。
- [OpenAI](https://developers.openai.com/api/docs/pricing)、[Claude](https://platform.claude.com/docs/en/about-claude/pricing)、[Gemini](https://ai.google.dev/gemini-api/docs/pricing) 官方计价示例，不是所有服务的统一条件。

当次两端点曾返回 `Cache-Control: public, max-age=0, must-revalidate`／ETag，是观察不是固定契约；未公布固定 QPS／SLA不代表无限请求或保证可用。社区数据可能滞后，`last_updated`、本地获取时间、价格生效时间不能混同。测试选取见[测试说明](testing.md)，线上整链及发布进度见 [TODO](../TODO.md#待验收与发布)。
