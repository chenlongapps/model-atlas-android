# 架构说明

本文描述当前已实现的真实 LLM 列表、网络请求、独立映射与 Room3 目录存储。搜索、详情、对比、收藏和离线缓存策略仍属于规划，任务状态见 [TODO.md](../TODO.md)。

## 代码入口与职责

项目只有 `:app` 模块。下表中的 Kotlin 路径均相对于 [`app/src/main/java/io/github/chenlongapps/modelatlas/`](../app/src/main/java/io/github/chenlongapps/modelatlas/)。

| 路径 | 当前职责 |
| --- | --- |
| [`ModelAtlasApplication.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ModelAtlasApplication.kt) | 通过 `@HiltAndroidApp` 初始化 Hilt |
| [`MainActivity.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/MainActivity.kt) | 应用入口，启用边到边显示，承载 `ModelAtlasTheme` 与 `ModelListRoute` |
| [`data/model/ModelSummary.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/model/ModelSummary.kt) | 独立列表摘要模型，包含稳定 ID、名称、发布时间、token 限制、输入／输出模态、开放权重与许可；LAB 从稳定 ID 前缀派生，不引用 DTO 或 Entity |
| [`data/model/ModelPrice.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/model/ModelPrice.kt) | `ModelPriceSummary` 与 `ModelPriceTierSummary`，单条展示报价与条件档位，口径为 USD / 百万 tokens |
| [`data/model/PriceProviderPolicy.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/model/PriceProviderPolicy.kt) | 官方服务商对应表与第三方优先级；决定同一模型多个报价中展示哪一家 |
| [`data/model/LlmInclusionPolicy.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/model/LlmInclusionPolicy.kt) | 基于类型与输入、输出模态的 LLM 收录规则 |
| [`data/local/entity/`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/entity/) | 三张 Room3 目录实体，区分规范模型、API 服务商和模型服务，详见[本地目录数据](local-data.md) |
| [`data/local/model/`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/model/) | 独立于 DTO 的本地 JSON 值对象，保存权重、链接、评测、完整报价、推理选项与模式等复杂信息 |
| [`data/local/converter/CatalogTypeConverters.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/converter/CatalogTypeConverters.kt) | Room3 JSON 列转换器，保留 null 与空集合、零价的区别，不将损坏数据伪装成默认值 |
| [`data/remote/ModelsDevParser.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/remote/ModelsDevParser.kt) | 将 Models.dev 两种 JSON 字符串分别解析为模型字典和服务商字典，不发起网络请求 |
| [`data/remote/ModelsDevApi.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/remote/ModelsDevApi.kt) | Retrofit 异步端点，与纯解析入口共用 `ModelsDevJson` |
| [`data/remote/dto/`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/remote/dto/) | `ModelDto`、`ProviderDto`、`ModelOfferingDto` 及嵌套结构；保留远程字段与缺失信息，独立于 `ModelSummary` 和数据库 Entity |
| [`data/mapper/`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/mapper/) | DTO → 独立本地结构；数据库投影 → LLM 列表应用模型 |
| [`data/local/CatalogDatabase.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/CatalogDatabase.kt)、[`CatalogDao.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/CatalogDao.kt) | `catalog.db`、完整快照事务写入、规范模型与 API 服务商左连接 Flow 以及独立的报价投影 Flow |
| [`data/local/ModelListRow.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/ModelListRow.kt) | 规范模型元数据与关联服务商的左连接投影，不包含报价 |
| [`data/local/ModelOfferingPriceRow.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/ModelOfferingPriceRow.kt) | 报价投影，只取显式关联且有报价的模型服务，不重复规范模型元数据 |
| [`data/local/CatalogStore.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/CatalogStore.kt) | 仓库使用的存储接口及 Room 实现，可替换为测试 fake |
| [`data/repository/`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/repository/) | `ModelRepository` 接口与 `DefaultModelRepository`；请求、获取时间、后台映射、限流冷却与目录写入 |
| [`di/DataModule.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/di/DataModule.kt) | 网络客户端、数据库、时钟、IO dispatcher 与接口绑定 |
| [`ui/modellist/ModelListViewModel.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListViewModel.kt) | 定义 `ModelListUiState`，读取仓库数据并暴露只读 `StateFlow` |
| [`ui/modellist/ModelListScreen.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListScreen.kt) | 包含 Route、列表界面、模型卡片及浅色／深色预览 |
| [`ui/modellist/LabLogo.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/LabLogo.kt) | LAB 前缀到随包彩色 logo 素材的映射与卡片 logo 组件，没有素材时显示兜底图 |
| [`ui/theme/Theme.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/theme/Theme.kt) | 定义 Compose 主题与颜色；Activity／窗口主题的修改位置见 [AGENTS.md](../AGENTS.md) |

## 数据与状态流转

```text
DefaultModelRepository.refresh()
    → 并行请求 models.json?type=all、api.json?type=all
    → CatalogMapper（分别记录两个响应的获取时间）
    → CatalogStore → CatalogDao.replaceCatalog()（全量快照事务）
    → CatalogDao.observeModelRows()（左连接并去重）
    → CatalogDao.observeOfferingPriceRows()（显式关联且有报价的模型服务）
    → ModelListMapper + LlmInclusionPolicy（去重、收录、发布时间倒序）+ selectPriceFor（挑选展示报价）→ Flow<List<ModelSummary>>
    → ModelListViewModel.uiState: StateFlow<ModelListUiState>
    → ModelListRoute（collectAsStateWithLifecycle）
    → ModelListScreen(uiState)
    → ModelCard(model)
```

- `ModelListViewModel` 初始化时请求刷新，成功后才订阅本地目录。状态包含 `models`、`isLoading` 和分类错误；首次失败不展示旧目录或示例。重复点击重试不会并发发起请求，协程取消不当作失败。
- `ModelListRoute` 负责获取 Hilt ViewModel 和生命周期感知的状态收集，不直接请求数据源。
- `ModelListScreen` 通过参数接收状态，不依赖 Hilt 或 ViewModel。修改时保留这一边界，使预览和后续界面测试可以使用独立数据。
- 列表使用 `models.json` 字典键作为稳定 `id` 和条目 key；每个规范模型一行。同名模型不合并，缺少服务商关联的规范模型仍展示；查询仍保留显式 `canonical_model_id` 左连接，摘要映射按规范模型 ID 去重，不再向界面传递服务商和描述。报价来自独立的 `observeOfferingPriceRows()` 投影，两条 Flow 在仓库层用 `combine` 合并后一起映射，因此规范模型元数据与报价不会互相污染。
- 数据库存储整个目录以保留未关联服务和完整报价；列表收录明确 `type = "llm"`，或无类型且输入、输出模态均包含 `text` 的记录。明确非 LLM、未知类型及无法判断的记录不收录。该规则不是模型能力认证。

## 当前实现边界

- 列表展示模型名、LAB、发布时间、上下文长度、最大输出、多模态能力和开放权重。LAB 取稳定 ID 第一个 `/` 前的非空白部分，没有分隔符或有效前缀时显示“暂无数据”，不视为独立核实的开发商。日期保留来源精度，限制使用完整 `Long` 数值并标注 tokens。
- 开放权重按 `open_weights` 三态展示：“权重公开”“未公开”“暂无数据”；仅在权重公开且 `license` 非空白时附带展示许可字符串。该字段是来源报告的权重可获取状态，不等于开源、允许任意用途或允许商用，界面来源说明已注明这一点。
- 列表在模型名左侧显示 LAB 彩色 logo：素材来自 Models.dev 的 Logos 服务，随包覆盖 23 个实验室，按稳定 ID 第一个 `/` 前的前缀精确匹配；没有素材（含 LAB 缺失）时显示兜底星光图，不借用其他实验室的素材，也不从服务商 ID 或名称反推对应关系。素材保留品牌原色、不随主题变色，因此使用固定浅色圆角底，保证深色图案在深色卡片上同样可读；logo 只是视觉标识，「研发机构」文本与「LAB 为 ID 前缀标签、未经独立核实」的说明仍按原样展示。
- 列表顺序由映射层固定按发布时间倒序（最新在前）：缺失或空白发布时间的模型排在最后，发布时间相同再按名称（不区分大小写）和稳定 ID 稳定排序。日期只按字符串字典序比较，不解析或补全，因此 `2025` 早于 `2025-06`、`2025-06` 早于 `2025-06-01`；DAO 仍按名称排序，仅作为去重前的稳定输入。当前不提供排序切换。
- 多模态能力分别展示输入和输出，已知模态翻译为中文，未知模态保留原值；缺失显示“暂无数据”，显式空集合显示“无”。列表字段仅取规范模型，不混用 API 服务商限制；服务商和摘要仍保留在本地目录。仅预览和测试保留合成样本，运行时没有 demo 数据。
- 卡片展示一条报价：以「价格（USD / 百万 tokens）」为小节标题，逐行展示输入、输出单价，以及来源提供的缓存命中输入、缓存写入、推理、音频输入、音频输出单价；单位统一为 USD / 百万 tokens。界面不展示服务商名称，也不标注“官方／第三方”，选取规则只在 `PriceProviderPolicy` 与 `selectPriceFor` 内部决定展示哪一家，该规则是应用内的展示顺序，不是质量或最低价排行榜。没有可用报价时显示「价格：暂无数据」，不补造零价；明确的 `0` 显示为 `$0.00`。`tiers` 的上下文档位默认折叠，点击「展开档位价格」后查看；列表底部与「数据来源与许可」弹窗都注明同一模型的不同服务商价格不可直接比较。
- 价格格式化至少保留两位小数（`0.6` → `$0.60`），来源更精确时保留更多位（`0.003` → `$0.003`），避免把高精度报价抹成 `$0.00`。
- 已实现目录落库，不等同于完整离线缓存：每个新的 ViewModel 请求一次，失败由用户重试，不轮询；尚未实现离线读取、失败回退、更新时间展示或缓存时效策略。
- 两端点均成功解析后才事务提交；失败或取消不会写入半份目录。HTTP 429 / 503 尊重 `Retry-After`（秒数或 HTTP 日期），缺失或非法时冷却 60 秒。OkHttp 不自动重试请求。
- 页面提供来源与第三方说明、随包 MIT 许可全文；[AndroidManifest.xml](../app/src/main/AndroidManifest.xml) 已声明普通 `INTERNET` 权限，不申请运行时危险权限。
- 当前没有模型详情或多页面导航流程。新增功能前先确认任务范围，不因依赖已存在就假定对应能力已实现。
- Models.dev 的收录、计价、能力与许可约定见[数据源说明](data-source.md)。

## 现有单元测试

测试源码位于 [`app/src/test/java/io/github/chenlongapps/modelatlas/`](../app/src/test/java/io/github/chenlongapps/modelatlas/)。

| 测试 | 已覆盖内容 |
| --- | --- |
| [`ModelRepositoryTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/repository/ModelRepositoryTest.kt) | 单次完整写入、失败不写半份、取消、数据库 Flow、空目录及限流冷却 |
| [`ModelListViewModelTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListViewModelTest.kt) | 加载、成功、空目录、状态更新、错误分类、重试去重与取消，不对外暴露 `MutableStateFlow` |
| [`ModelsDevApiTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/remote/ModelsDevApiTest.kt) | MockWebServer 验证真实 Retrofit 路径、`type=all`、两个端点的解析与 HTTP 错误不自动重试 |
| [`CatalogMapperTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/mapper/CatalogMapperTest.kt)、[`ModelListMapperTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/mapper/ModelListMapperTest.kt) | 独立 ID、显式关联、完整复杂字段、缺失信息、规范模型去重、列表元数据映射、开放权重与许可三态、发布时间倒序、报价投影挂载与未匹配报价行 |
| [`ModelPriceMapperTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/mapper/ModelPriceMapperTest.kt) | 官方优先、第三方优先级、同服务商取最低、零价与缺失价、纯档位报价、无服务商名称、缓存／推理／音频与旧版字段 |
| [`PriceProviderPolicyTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/model/PriceProviderPolicyTest.kt) | 已核实的第一方对应表、无官方对应的 LAB、第三方优先级顺序与未列入者排序 |
| [`ModelSummaryTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/model/ModelSummaryTest.kt) | LAB 的首段前缀提取、无分隔符与空白前缀边界 |
| [`LabLogoTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/LabLogoTest.kt) | 23 个已收录 LAB 的素材映射、未知与空白 LAB 不借用其他素材、缺失 LAB 使用兜底图、LAB 取稳定 ID 前缀 |
| [`LlmInclusionPolicyTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/model/LlmInclusionPolicyTest.kt) | LLM、多模态、非 LLM、未知类型与缺失模态的收录边界 |
| [`ModelsDevParserTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/remote/ModelsDevParserTest.kt) | 两种响应的字段、嵌套结构、标识、缺失与空值、未知字段和错误响应 |
| [`InterleavedDtoSerializerTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/remote/InterleavedDtoSerializerTest.kt) | `interleaved` 的布尔／对象形式及空值往返序列化、未知字段和非法类型 |
| [`CatalogTypeConvertersTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/local/converter/CatalogTypeConvertersTest.kt) | 全部本地 JSON 转换器的空值与往返转换、完整报价、复杂模式、开放字符串、未知字段和错误类型 |
| [`CatalogEntitiesTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/local/entity/CatalogEntitiesTest.kt) | 本地实体的默认未知值、独立标识、显式获取时间及模型／服务元数据边界 |

测试目录另有仅供 Room KSP 编译检查的 [`CatalogSchemaTestDatabase`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/local/CatalogSchemaTestDatabase.kt)，不创建运行时数据库。异步单元测试使用 `kotlinx-coroutines-test` 和手写 fake，不依赖公网。

`androidTest` 中的 `CatalogDatabaseTest` 使用实际 SQLite 验证关联、复合服务 ID、事务回滚、快照替换、文件重开、报价投影只取显式关联且有报价的服务以及未匹配报价行不会变成模型；`ModelListScreenTest` 使用 Compose Test APIs v2 验证真实字段、缺失值、开放权重与许可展示、加载、空目录、重试、许可说明与弹窗状态恢复，以及报价的官方／第三方标记、输入输出单价与单位、零价与缺失价、价格明细展开收起和不可直接比较提示，还包括已知 LAB 显示随包 logo、无素材与缺失 LAB 显示兜底图。构建与测试命令统一见 [AGENTS.md](../AGENTS.md)。
