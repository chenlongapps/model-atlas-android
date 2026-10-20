# 架构说明

本文描述当前已实现的真实 LLM 列表、网络请求、独立映射、Room3 目录存储与本地优先刷新策略。搜索、详情、对比、收藏和目录更新时间展示仍属于规划，任务状态见 [TODO.md](../TODO.md)。

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
| [`data/repository/`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/repository/) | `ModelRepository` 接口与 `DefaultModelRepository`；请求、获取时间、后台映射、完整内容比较、暂存待更新快照、限流冷却与目录写入 |
| [`di/DataModule.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/di/DataModule.kt) | 网络客户端、数据库、时钟、IO dispatcher 与接口绑定 |
| [`ui/modellist/ModelListViewModel.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListViewModel.kt) | 定义 `ModelListUiState`，读取仓库数据并暴露只读 `StateFlow` |
| [`ui/modellist/ModelListScreen.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListScreen.kt) | 包含 Route、列表界面、加载／错误／空目录状态卡片、许可弹窗及预览 |
| [`ui/modellist/ModelCard.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelCard.kt) | 紧凑模型卡片及独立预览、图标参数行、方向模态标签、分列价格区与档位、数值格式化 |
| [`ui/modellist/LabLogo.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/LabLogo.kt) | LAB 前缀到随包彩色 logo 素材的映射与卡片 logo 组件，没有素材时显示兜底图 |
| [`ui/theme/Theme.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/theme/Theme.kt) | 定义 Compose 主题与颜色；Activity／窗口主题的修改位置见 [AGENTS.md](../AGENTS.md) |

## 数据与状态流转

```text
本地目录 → ModelListViewModel 立即展示并持续观察

DefaultModelRepository.checkForUpdates() / refresh()
    → 并行请求 models.json?type=all、api.json?type=all
    → CatalogMapper（分别记录两个响应的获取时间）
    → 与本地完整快照比较（忽略本地抓取时间、字典顺序）
    → 后台检查：仅暂存变化快照、提示可更新 → 用户确认 applyPendingUpdate()
    → 主动刷新：直接提交变化快照
    → CatalogStore → CatalogDao.replaceCatalog()（全量快照事务）
    → CatalogDao.observeModelRows()（左连接并去重）
    → CatalogDao.observeOfferingPriceRows()（显式关联且有报价的模型服务）
    → ModelListMapper + LlmInclusionPolicy（去重、收录、发布时间倒序）+ selectPriceFor（挑选展示报价）→ Flow<List<ModelSummary>>
    → ModelListViewModel.uiState: StateFlow<ModelListUiState>
    → ModelListRoute（collectAsStateWithLifecycle）
    → ModelListScreen(uiState)
    → ModelCard(model)
```

- `ModelListViewModel` 初始化先读取并观察本地目录；存在任一目录记录时立即展示（即使筛选后的 LLM 列表为空），后台仅检查更新，不自动覆盖数据库。无本地记录时自动获取并提交，首次失败展示分类错误与重试，不使用示例。
- 状态区分 `isLoading`、`isRefreshing`、`updateAvailable`、阻断错误 `error` 和非阻断的刷新结果／错误。后台检查失败静默保留本地目录；主动刷新失败用 Snackbar 提示，不清空列表。底部持续 Snackbar 提供“立即更新”，确认后提交已获取快照，不重复下载；下拉刷新直接获取并应用，若正在检查则复用当前请求并在完成后提交。初次加载、检查、提交和主动刷新统一防重入，取消不当作失败。
- `ModelListScreen` 通过 Material 3 `PullToRefreshBox` 提供下拉刷新（空目录同样支持），指示器避开顶部系统栏，并为读屏提供“刷新目录”自定义操作。Snackbar 不改变列表页头的普通滚动行为。
- `ModelListRoute` 负责获取 Hilt ViewModel 和生命周期感知的状态收集，不直接请求数据源。
- `ModelListScreen` 通过参数接收状态，不依赖 Hilt 或 ViewModel。修改时保留这一边界，使预览和后续界面测试可以使用独立数据。
- 页面不使用 `TopAppBar`、Scaffold 的 `topBar` 插槽或应用栏专用滚动行为。标题、目录数量与来源许可入口放在 Scaffold 内容区内，作为 `LazyColumn` 的首项 `CatalogHeader`，与模型卡片同速滚动，回到列表顶部时才再次显示，不固定、不吸顶，也不在下滑时单独弹出。Scaffold 最后叠加随主题变化的半透明状态栏渐变保护层，不占布局空间、不拦截触摸。
- 列表使用 `models.json` 字典键作为稳定 `id` 和条目 key；每个规范模型一行。同名模型不合并，缺少服务商关联的规范模型仍展示；查询仍保留显式 `canonical_model_id` 左连接，摘要映射按规范模型 ID 去重，不再向界面传递服务商和描述。报价来自独立的 `observeOfferingPriceRows()` 投影，两条 Flow 在仓库层用 `combine` 合并后一起映射，因此规范模型元数据与报价不会互相污染。
- 数据库存储整个目录以保留未关联服务和完整报价；列表收录明确 `type = "llm"`，或无类型且输入、输出模态均包含 `text` 的记录。明确非 LLM、未知类型及无法判断的记录不收录。该规则不是模型能力认证。

## 当前实现边界

- 列表展示模型名、LAB 文字与 logo、发布时间、上下文长度、最大输出、多模态能力和开放权重。LAB 取稳定 ID 第一个 `/` 前的非空白部分，没有分隔符或有效前缀时显示“暂无数据”，不视为独立核实的开发商。日期保留来源精度，限制在数据层保留完整 `Long`，列表视觉使用十进制 K／M 缩写、最多一位小数并去除末尾零（`1,000,000 → 1M`、`384,000 → 384K`、`131,072 → 131.1K`）；读屏仍提供字段名、带千位分隔的精确值和 tokens 单位。上下文长度使用横向扩展图标（`ic_context_window`），最大输出使用文字输出箭头图标（`ic_output_limit`），均为随主题着色的 16dp 描边图标，视觉上不重复单位。
- 列表保留参考 RvSystem Monitor 的圆角结构，模型卡片仅按新的参考图调整紧凑布局、不复制配色：24dp 圆角、16dp 内边距与 16dp 列表间距；56dp logo 右侧显示模型名、LAB、合并的权重许可标签，下面是可换行的图标参数行；输入与输出模态标签横排，中间以指向输出的箭头区分，不显示“输出：”前缀，读屏保留输入／输出说明，窄屏可换行。模型能力与价格之间仅用一条细分隔线分区，价格不再嵌套独立背景或圆角卡片；输入／输出及存在时的缓存命中输入仍以主题色突出、列间显示细分隔线，按价格区可用宽度和字号调整为 1–3 列，窄屏时不额外缩小字体、截断单价或将价格改为近似值。顶部目录数量、来源许可入口与既有发布时间倒序不变，不新增详情跳转或导航。
- 主题仍使用现有 Material 3 依赖；Android 12 及以上使用系统动态浅色／深色调色板，低版本使用固定备用色。预览关闭动态取色，保持可复现；测试可通过 `ModelAtlasTheme(dynamicColor = false)` 固定配色。加载、空目录、分类错误和许可弹窗统一采用圆角与色调分层。Activity 的 `enableEdgeToEdge()` 继续管理系统栏图标颜色，保留时间、电量等系统信息。页面合并系统栏与显示缺口 insets，Scaffold 将安全区传给列表的 `contentPadding`，不在列表或其父容器添加系统栏 padding，因此初始标题、按钮和首条内容避开系统图标，滚动内容仍可延伸到状态栏下方；底部安全区不变。
- 开放权重按 `open_weights` 三态展示：“开放权重”“权重未公开”“开放权重：暂无数据”；仅在权重公开且 `license` 去除首尾空白后为 1–16 个字符时，以中点附带原始许可短名，如“开放权重 · MIT”“开放权重 · Apache-2.0”。长名称不截断、不猜测简称，列表仅显示“开放权重”，完整原文继续保留在模型与本地目录中，未来详情页再展示（详情页尚未实现）。该字段是来源报告的权重可获取状态，不等于开源、允许任意用途或允许商用，界面来源说明已注明这一点。
- 列表在模型名左侧显示 LAB 彩色 logo：素材来自 Models.dev 的 Logos 服务，随包覆盖 23 个实验室，按稳定 ID 第一个 `/` 前的前缀精确匹配；没有素材（含 LAB 缺失）时显示兜底星光图，不借用其他实验室的素材，也不从服务商 ID 或名称反推对应关系。素材保留品牌原色、不随主题变色，因此使用固定白色圆角底，保证深色图案在深色卡片上同样可读；logo 只是视觉标识，「LAB 为 ID 前缀标签、未经独立核实」的说明保留。
- 列表顺序由映射层固定按发布时间倒序（最新在前）：缺失或空白发布时间的模型排在最后，发布时间相同再按名称（不区分大小写）和稳定 ID 稳定排序。日期只按字符串字典序比较，不解析或补全，因此 `2025` 早于 `2025-06`、`2025-06` 早于 `2025-06-01`；DAO 仍按名称排序，仅作为去重前的稳定输入。当前不提供排序切换。
- 多模态能力分别展示输入和输出，标签仅显示图标、不显示文字，读屏保留模态描述：已知模态翻译为中文，未知模态保留原值；缺失描述为“暂无数据”，显式空集合描述为“无”。文本模态使用 16dp 的 T 形描边图标（`ic_text`），随主题着色；PDF 保留文档图标。列表字段仅取规范模型，不混用 API 服务商限制；服务商和摘要仍保留在本地目录。仅预览和测试保留合成样本，运行时没有 demo 数据。
- 卡片展示一条报价：以「API 价格 · 美元 / 1M tokens」为小节标题，分列展示输入、输出及存在时的缓存命中输入单价，逐行展示来源提供的缓存写入、推理、音频输入、音频输出单价；单位统一为 USD / 百万 tokens。价格区直接沿用外层模型卡片的 `surface` 背景，标题和价格使用 `primary`，权重公开标签使用 `tertiaryContainer` / `onTertiaryContainer`，跟随系统动态浅色／深色调色板，不硬编码参考图中的绿色。界面不展示服务商名称，也不标注“官方／第三方”或数据未提供的“非高峰”，选取规则只在 `PriceProviderPolicy` 与 `selectPriceFor` 内部决定展示哪一家，该规则是应用内的展示顺序，不是质量或最低价排行榜。没有可用报价时显示「价格：暂无数据」，不补造零价；明确的 `0` 显示为 `$0.00`。`tiers` 的上下文档位默认折叠，有档位时标题行显示箭头并可点击展开／收起，读屏提供动作与状态；无档位时不显示无效箭头。列表底部与「数据来源与许可」弹窗都注明同一模型的不同服务商价格不可直接比较。
- 价格格式化至少保留两位小数（`0.6` → `$0.60`），来源更精确时保留更多位（`0.003` → `$0.003`），避免把高精度报价抹成 `$0.00`。
- 价格区采用无独立背景的 `PriceSection`，取消内层圆角容器与额外内边距，标题、指标与分隔线左对齐；标题与计价单位统一采用 `labelSmall`（默认 11sp／16sp 行高），弱化说明文字、突出 `titleMedium` 单价，保留系统字号缩放。标题箭头缩至 16dp，不再设置 48dp 的布局最小高度，默认标题行随内容约为 16dp；`clickable` 在布局外扩展触摸区域，维持至少 48dp 的触摸高度，附近无其他可点击控件。标题与指标等区域间距 4dp，指标内部间距 2dp，换行间距 8dp。仍保留输入／输出／缓存分列、完整价格精度、单位及其他类别；档位门槛显示完整数值，不使用 K／M 近似。
- 档位展开箭头遵循折叠区惯例：收起时朝下，展开后朝上，通过 0°／180° 旋转动画过渡；不使用目录树式的朝右／朝下组合。整行点击和读屏动作／状态不变，UI 回归比较箭头实际绘制的尖端方向，覆盖展开与再次收起。
- 已实现本地优先与离线浏览：每个新的 ViewModel 有本地目录时后台检查一次，没有时自动加载；不轮询、不增加 WorkManager 或退出后的后台任务。更新检测涵盖全部模型、服务商和模型服务的新增、删除、参数与报价等变化，不仅限于当前列表字段；不将本地抓取时间或字典顺序变化当作更新。待应用快照仅暂存在仓库内存中，进程重启后重新检查；内容一致时不重复写库。尚未展示更新时间，也未引入 TTL 策略。
- 两端点均成功解析后才事务提交；失败或取消不会写入半份目录。HTTP 429 / 503 尊重 `Retry-After`（秒数或 HTTP 日期），缺失或非法时冷却 60 秒。OkHttp 不自动重试请求。
- 页面提供来源与第三方说明、随包 MIT 许可全文；[AndroidManifest.xml](../app/src/main/AndroidManifest.xml) 已声明普通 `INTERNET` 权限，不申请运行时危险权限。
- 当前没有模型详情或多页面导航流程。新增功能前先确认任务范围，不因依赖已存在就假定对应能力已实现。
- Models.dev 的收录、计价、能力与许可约定见[数据源说明](data-source.md)。

## 现有单元测试

测试源码位于 [`app/src/test/java/io/github/chenlongapps/modelatlas/`](../app/src/test/java/io/github/chenlongapps/modelatlas/)。

| 测试 | 已覆盖内容 |
| --- | --- |
| [`ModelRepositoryTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/repository/ModelRepositoryTest.kt) | 完整写入、失败不写半份、取消、数据库 Flow、空目录、限流冷却、检查不写库、应用不重复下载、无变化不写库、手动刷新取代暂存快照与提交失败可重试 |
| [`ModelListViewModelTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListViewModelTest.kt) | 首次加载与重试、本地优先、后台检查、主动应用、刷新失败保留数据、检查中下拉复用请求、刷新去重与取消，不对外暴露 `MutableStateFlow` |
| [`CatalogSnapshotTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/local/CatalogSnapshotTest.kt) | 完整内容比较、忽略抓取时间与字典顺序、增删及参数／报价变化、保留缺失与空集合／零值区别 |
| [`ModelsDevApiTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/remote/ModelsDevApiTest.kt) | MockWebServer 验证真实 Retrofit 路径、`type=all`、两个端点的解析与 HTTP 错误不自动重试 |
| [`CatalogMapperTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/mapper/CatalogMapperTest.kt)、[`ModelListMapperTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/mapper/ModelListMapperTest.kt) | 独立 ID、显式关联、完整复杂字段、缺失信息、规范模型去重、列表元数据映射、开放权重与许可三态、发布时间倒序、报价投影挂载与未匹配报价行 |
| [`ModelPriceMapperTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/mapper/ModelPriceMapperTest.kt) | 官方优先、第三方优先级、同服务商取最低、零价与缺失价、纯档位报价、无服务商名称、缓存／推理／音频与旧版字段 |
| [`PriceProviderPolicyTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/model/PriceProviderPolicyTest.kt) | 已核实的第一方对应表、无官方对应的 LAB、第三方优先级顺序与未列入者排序 |
| [`ModelSummaryTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/model/ModelSummaryTest.kt) | LAB 的首段前缀提取、无分隔符与空白前缀边界 |
| [`LabLogoTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/LabLogoTest.kt) | 23 个已收录 LAB 的素材映射、未知与空白 LAB 不借用其他素材、缺失 LAB 使用兜底图、LAB 取稳定 ID 前缀 |
| [`ModelPriceFormatTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelPriceFormatTest.kt) | 零价、至少两位小数、高精度小额报价不被舍入为零 |
| [`ModelTokenFormatTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelTokenFormatTest.kt) | 列表 K／M 缩写、一位小数与单位进位边界、零值与 Long 极值，以及读屏和档位使用的精确千位分隔格式 |
| [`ModelLicenseFormatTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelLicenseFormatTest.kt) | 保留原始许可短名、首尾空白、16 字符边界及空白／长名称不展示，不推测许可简称 |
| [`LlmInclusionPolicyTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/model/LlmInclusionPolicyTest.kt) | LLM、多模态、非 LLM、未知类型与缺失模态的收录边界 |
| [`ModelsDevParserTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/remote/ModelsDevParserTest.kt) | 两种响应的字段、嵌套结构、标识、缺失与空值、未知字段和错误响应 |
| [`InterleavedDtoSerializerTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/remote/InterleavedDtoSerializerTest.kt) | `interleaved` 的布尔／对象形式及空值往返序列化、未知字段和非法类型 |
| [`CatalogTypeConvertersTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/local/converter/CatalogTypeConvertersTest.kt) | 全部本地 JSON 转换器的空值与往返转换、完整报价、复杂模式、开放字符串、未知字段和错误类型 |
| [`CatalogEntitiesTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/local/entity/CatalogEntitiesTest.kt) | 本地实体的默认未知值、独立标识、显式获取时间及模型／服务元数据边界 |

测试目录另有仅供 Room KSP 编译检查的 [`CatalogSchemaTestDatabase`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/local/CatalogSchemaTestDatabase.kt)，不创建运行时数据库。异步单元测试使用 `kotlinx-coroutines-test` 和手写 fake，不依赖公网。

`androidTest` 中的 `CatalogDatabaseTest` 使用实际 SQLite 验证关联、复合服务 ID、事务回滚、快照替换、文件重开、报价投影只取显式关联且有报价的服务以及未匹配报价行不会变成模型；`ModelListScreenTest` 使用 Compose Test APIs v2 验证真实字段、方向模态标签、缺失值、开放权重与短许可展示（长许可隐藏）、加载、空目录、重试、许可说明与弹窗状态恢复，以及报价不显示官方／第三方标记、输入输出单价与单位、零价与缺失价、价格明细展开收起与状态恢复、档位标题至少 48dp 点击高度和不可直接比较提示，还包括 LAB logo 及兜底图、目录数量、Token 缩写与精确读屏语义、双倍字号下长名称和缩写数值不溢出。`ModelAtlasThemeTest` 验证固定与系统动态浅色／深色调色板，另有 API 30 及以下的备用配色用例。构建与测试命令统一见 [AGENTS.md](../AGENTS.md)。

状态栏回归用例通过 `ModelListScreen` 的 `windowInsets` 参数固定顶部高刘海与底部安全区，覆盖浅色、深色及双倍字号；除检查初始内容和底部说明避开安全区、标题随列表滚走及回到顶部后恢复外，还比较状态栏区域的实际绘制像素，避免仅验证布局边界而漏掉实色背景遮挡。另有保持手指按下的拖动用例，检查标题与来源许可按钮的实际位移，并确认页头与模型卡片同速滚动；滚动到列表中部后下滑不会单独显示页头，回到顶部仍可打开许可弹窗。

价格区的浅色／深色回归用例比较价格区与外层模型卡片的实际背景像素，并检查标题、价格区与能力分隔线左对齐，防止重新引入内层卡片底色或额外缩进。

刷新回归增加实际下拉手势（有数据与空目录）、更新提示的显式确认操作、刷新失败仍显示本地模型及数量；数据库回归增加完整快照读取与目录存在性查询。
