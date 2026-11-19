# 数据源说明

## 当前来源与边界

| 内容 | 当前来源 | 契约 |
| --- | --- | --- |
| 规范模型、评测成绩／定义、中文 | ModelAtlas Data Pages | [Atlas 发布包](atlas-data.md) |
| 服务商、模型服务、价格／服务限制 | Models.dev `api.json?type=all` | 本文 |
| 首次离线模型／评测／翻译 | 同协议 `assets/atlas/` 真实包，无价格 | [目录初始化](architecture.md#数据与状态流转) |

只支持表中的当前协议。规范模型和 API 服务仅按显式 `canonical_model_id` 关联；不按名称或 LAB 猜测，也不跨层补参数或合并能力。

## 请求与解析

- `ModelsDevApi` 下载公开目录，无需 API Key；`env` 是下游服务的环境变量名。`ModelsDevParser` 解析服务商字典及其中的 `models` 服务字典；两层键和条目内 ID 分开保存。
- `id`、`name`、服务商 `models` 必须存在且非 null，空字典合法。未知对象字段忽略，已知字段类型错误不回退默认值；可选字段区分 null、false、零、空集合。token 限制和预算用 Long，日期保留原精度。
- `interleaved` 只接受布尔或 `{"field":"reasoning_content"}`。完整保留七类价格、`tiers`、旧档位及模式覆盖；请求 `body`／headers 只存储，不展示或执行。
- 连接 15s、读取 30s、整次 60s，不自动重试。仓库对 429／503 使用 `Retry-After` 秒数或 HTTP 日期，缺失／非法则冷却 60s，冷却中不发请求。原子更新见[架构](architecture.md#数据与状态流转)。

### 收录与规范模型语义

- 每个规范 ID 一行，同名不合并，无服务仍展示；明确 `type = "llm"` 收录，类型缺失时须输入／输出均含 `text`。明确非 LLM、未知／空类型或模态不足不收录，不是能力认证。
- LAB 仅为规范 ID 第一个 `/` 前非空前缀，无有效前缀为未知；不是 API 服务商或独立核实的开发商。品牌显示名与 Logo 不改变底层 ID、搜索或关联规则。
- 发布日期保留来源精度，映射按日期字符串倒序，缺失最后，同日按名称（忽略大小写）和 ID 稳定排序；不补日期，也不聚合服务限制。
- 规范能力与参数格式见 [Atlas 契约](atlas-data.md)。

## 计价约定

所有 `cost`（含 `tiers`／模式）统一为 **USD / 1,000,000 tokens**。非美元由上游换算，汇率／日期在源 TOML 注释、不在 JSON 中，不再次换汇。价格至少显示两位小数，保留来源更高精度。

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

可用报价至少有一项基础或 `tiers` 输入／输出价，0 有效；仅有旧 `context_over_200k`、全空 cost 或 null 不展示默认报价。详情显示全部显式关联服务（含无价），按第一方／第三方优先级、服务商 ID 和服务 ID 排序，不筛最低价。可见字段见[页面字段](page-information.md)。

### 报价范围与适用条件

- 价格属于具体服务。null 是未知，明确 0 仅该字段为零，不代表永久免费、无需订阅或无额度。
- `tiers[].tier.type = "context"` 的 `size` 是精确 token 条件门槛，不是模型上下文或单次数量价格；等号归属、输入／缓存计入方式及整次请求如何应用以服务商为准，不假设超额累进计费。
- `context_over_200k` 是兼容字段，不表示精确门槛为 200,000。模式价／请求覆盖与基础价分开，不推断组合条件或适用档位，不计算总价。
- 缓存、地区、批处理、订阅、额度、促销及附加费用以服务商条款为准。推理费可能已含在输出费中，不能直接相加；相同价格单位不意味着相同任务成本。

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

这些是来源报告，不是应用实测或组合能力保证。参数量只读 [Atlas 扩展](atlas-data.md#模型参数量)。

## 数据许可与来源署名

- Models.dev 仓库为 MIT，`Copyright (c) 2025 models.dev`；分发重大部分须保留完整版权及免责文本，仅名称／链接不替代许可。`res/raw/models_dev_license.txt` 随包，设置来源子页离线可读。
- 独立第三方说明与许可保留是两件事；MIT 不决定本项目许可证、不替代权重／服务／商标条款。Atlas 许可与随包维护见[接入契约](atlas-data.md#许可与随包维护)。

### Logo 映射与维护

- LAB 源为 Models.dev `/logos/labs/{lab}.svg` 或同图本地彩色变体；`LabLogo.kt` 按 LAB 前缀映射。
- 服务商源为 Models.dev `/logos/{provider-ID}.svg`；`ProviderLogo.kt` 按稳定 ID 精确映射。只有原 SVG 相同才复用资源。
- 不在运行时下载。库中通用图、无法完整转换或未知 ID 用各自兜底，不互借 Logo。用 Svg2Vector 转换时保留比例和品牌原色，更新须同步映射、测试和资源来源注释。评测素材另见[评测 Logo](benchmark-logos.md)。

## 核实依据与时效

计价口径于 2026-10-19 对照上游固定提交 `00b5db7ae590a0e3cbafb2d64df4da111c49c1e7` 核实，不代表验证全部实时报价：
- [README](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/README.md)、[维护规范](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/AGENTS.md)：币种、单位、覆盖及推理控制。
- [Schema](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/packages/core/src/schema.ts)、[生成代码](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/packages/core/src/generate.ts)、[LICENSE](https://github.com/anomalyco/models.dev/blob/00b5db7ae590a0e3cbafb2d64df4da111c49c1e7/LICENSE)。
- [OpenAI](https://developers.openai.com/api/docs/pricing)、[Claude](https://platform.claude.com/docs/en/about-claude/pricing)、[Gemini](https://ai.google.dev/gemini-api/docs/pricing) 官方计价示例，不是所有服务的统一条件。

社区数据可能滞后，`last_updated`、本地获取时间和价格生效时间不能混同。
