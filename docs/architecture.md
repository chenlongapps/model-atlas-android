# 架构说明

本文描述当前已实现的真实 LLM 列表、本地搜索与筛选、模型详情、网络请求、独立映射、Room3 目录存储、本地优先刷新策略，以及模型／设置双标签与悬浮玻璃导航。设置页提供“数据来源与许可”独立页面入口；详情提供全部显式关联服务和记录级来源时效。对比、收藏和目录页统一更新时间展示仍属于规划，任务状态见 [TODO.md](../TODO.md)。

## 代码入口与职责

项目只有 `:app` 模块。下表中的 Kotlin 路径均相对于 [`app/src/main/java/io/github/chenlongapps/modelatlas/`](../app/src/main/java/io/github/chenlongapps/modelatlas/)。

| 路径 | 当前职责 |
| --- | --- |
| [`ModelAtlasApplication.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ModelAtlasApplication.kt) | 通过 `@HiltAndroidApp` 初始化 Hilt |
| [`MainActivity.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/MainActivity.kt) | 应用入口，启用边到边显示，承载 `ModelAtlasTheme` 与 `ModelAtlasApp`，关闭系统导航栏对比度遮罩 |
| [`ui/ModelAtlasApp.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/ModelAtlasApp.kt) | 两个标签页的外壳，通过 `HorizontalPager` 切换模型与设置，记录页面背景供玻璃导航采样，并向页面传递悬浮栏避让高度 |
| [`ui/navigation/GlassNavigationBar.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/navigation/GlassNavigationBar.kt) | Backdrop 悬浮玻璃胶囊、连续跟随分页偏移的选中块、点击和拖动切换、选中图标和颜色过渡及震动反馈 |
| [`ui/settings/SettingsScreen.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/settings/SettingsScreen.kt) | 设置页面，提供“数据来源与许可”入口 |
| [`ui/settings/DataSourceScreen.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/settings/DataSourceScreen.kt) | 独立说明页，集中展示数据来源、第三方身份、价格适用条件和随包 MIT 许可全文，提供返回按钮 |
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
| [`ui/modellist/ModelListViewModel.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListViewModel.kt) | 定义 `ModelListUiState`，读取仓库数据、管理查询条件与派生结果，以 `SavedStateHandle` 保存查询条件，暴露只读 `StateFlow` |
| [`ui/modellist/ModelListQuery.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListQuery.kt) | 可序列化查询条件、开放权重筛选与保序的纯匹配函数，不访问网络或数据库 |
| [`ui/modellist/ModelCatalogControls.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelCatalogControls.kt) | 胶囊搜索框、可移除条件标签和确认应用的筛选底部面板，选项由完整目录和草稿已选条件派生 |
| [`ui/modellist/ModelFilterFormat.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelFilterFormat.kt) | 筛选门槛使用精确 K／M 缩写，不沿用模型卡片的一位小数近似值 |
| [`ui/modellist/LabFilterOrder.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/LabFilterOrder.kt) | 筛选 LAB 选项和已选标签共用的前八位固定优先级，其余按完整目录模型数量降序，同数量按字母序兜底 |
| [`ui/modellist/ModelListScreen.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListScreen.kt) | 包含 Route、列表界面、查询入口、首次加载骨架、错误／空目录／无匹配状态卡片及预览，不再承载来源入口、价格说明卡片或许可弹窗 |
| [`ui/modellist/ModelCardSkeleton.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelCardSkeleton.kt) | 模型卡片骨架、整组共享的 Shimmer 动画与加载语义；根据生命周期和分页可见性暂停动画 |
| [`ui/modellist/CatalogStatusNotice.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/CatalogStatusNotice.kt) | 页头下的内联状态提醒及淡入／淡出、高度过渡，展示待更新、自动消失的成功结果与非阻断错误，提供确认更新、重试和错误手动关闭；刷新期间不显示加载卡片 |
| [`ui/modellist/ModelCard.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelCard.kt) | 紧凑模型卡片及独立预览、图标参数行、方向模态标签、分列价格区与档位、数值格式化 |
| [`ui/model/ModelPresentation.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/model/ModelPresentation.kt) | 列表与详情共用的横排／居中身份、权重许可和方向模态标签、紧凑／突出参数；缩写保留精确读屏语义，不依赖 DTO、Entity 或注入，身份元素保留独立 Modifier 接口，并提供列表／详情浅深色、窄屏和大字号预览 |
| [`ui/model/ModelSharedTransition.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/model/ModelSharedTransition.kt) | 按规范模型 ID 匹配整卡与全屏详情的容器变换，以及按模型 ID + 角色匹配的嵌套身份共享过渡；动态圆角裁剪与底色、临时身份快照及保留内容的可见性 Transition，无宿主时保留独立预览 |
| [`ui/modeldetail/ModelDetailHeader.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modeldetail/ModelDetailHeader.kt) | 固定返回栏的品牌／模型名滚动交接，以及放大的居中身份、描述、能力标签与独立参数卡；回报大标题在首条内的实测位置，绘制沿用页面背景 |
| [`ui/modellist/LabLogo.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/LabLogo.kt) | LAB 前缀到随包彩色 logo 素材的映射与卡片 logo 组件，没有素材时显示兜底图 |
| [`ui/modellist/LabDisplayName.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/LabDisplayName.kt) | LAB ID 到品牌惯用名称的展示映射，供模型卡片、筛选选项与已选标签共用；未知 LAB 保留原文，不改变底层 ID |
| [`data/local/ModelDetailRecord.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/ModelDetailRecord.kt) | 详情事务投影：规范模型、全部显式关联服务及各自服务商 |
| [`data/model/ModelDetail.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/model/ModelDetail.kt)、[`data/mapper/ModelDetailMapper.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/mapper/ModelDetailMapper.kt) | 独立详情应用模型与映射，完整单价／档位／独立模式、能力三态和链接，不向 UI 传递 Entity 或原始请求配置 |
| [`ui/modeldetail/ModelDetailViewModel.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modeldetail/ModelDetailViewModel.kt) | 只观察当前模型的本地详情，管理加载／内容／不存在／读取错误与本地重试；切换或关闭取消旧订阅 |
| [`ui/modeldetail/ModelDetailScreen.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modeldetail/ModelDetailScreen.kt)、[`DetailLink.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modeldetail/DetailLink.kt) | Route 收集生命周期感知状态，独立 Screen 展示精确参数与分组服务报价；外链校验及打开失败反馈 |
| [`ui/theme/Theme.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/theme/Theme.kt) | 定义 Compose 主题与颜色；Activity／窗口主题的修改位置见 [AGENTS.md](../AGENTS.md) |

## 数据与状态流转

```text
本地目录 → ModelListViewModel 立即展示并持续观察

DefaultModelRepository.checkForUpdates() / refresh()
    → 并行请求 models.json?type=all、api.json?type=all
    → CatalogMapper（分别记录两个响应的获取时间）
    → 与本地完整快照比较（忽略本地抓取时间、字典顺序）
    → 后台检查：规范模型 ID 集合变化 → 暂存完整快照、提示可更新 → 用户确认 applyPendingUpdate()
                 ID 集合不变、仅内容变化 → 静默提交完整快照，无提醒；内容一致不写库
    → 主动刷新：直接提交变化快照
    → CatalogStore → CatalogDao.replaceCatalog()（全量快照事务）
    → CatalogDao.observeModelRows()（左连接并去重）
    → CatalogDao.observeOfferingPriceRows()（显式关联且有报价的模型服务）
    → ModelListMapper + LlmInclusionPolicy（去重、收录、发布时间倒序）+ selectPriceFor（挑选展示报价）→ Flow<List<ModelSummary>>
    → ModelListViewModel.uiState: StateFlow<ModelListUiState>（完整 models + query → visibleModels）
    → ModelListRoute（collectAsStateWithLifecycle）
    → ModelListScreen(uiState)
    → ModelCard(model)
```

- `ModelListViewModel` 初始化先读取并观察本地目录；存在任一目录记录时立即展示（即使筛选后的 LLM 列表为空），后台检查时仅规范模型增删需要确认，模型 ID 集合不变时静默同步内容。无本地记录时自动获取并提交，首次失败展示分类错误与重试，不使用示例。
- 无本地目录的首次加载及首次失败重试在刷新提交后重新订阅目录查询，收到首个已提交结果后一次性发布 `models` 与 `isLoading = false`，再持续观察目录；不把网络／写库完成当作列表就绪，避免数据库 Flow 通知滞后时闪现空目录。真实空结果同样结束加载，不等待列表非空，也不人为延迟。
- 状态区分 `isLoading`、`isRefreshing`、`updateAvailable`、阻断错误 `error` 和非阻断的刷新结果／错误。后台检查失败静默保留本地目录；主动刷新失败用页头下的内联提醒提示，不清空列表。`checkForUpdates()` 返回 `CatalogUpdateCheckResult`，区分无变化、已静默更新和模型增删待确认；静默更新不设置待更新或成功消息。待更新提醒提供“立即更新”，确认后提交已获取完整快照，不重复下载；下拉刷新直接获取并应用，若正在检查则复用当前请求：待确认时提交暂存快照，已静默更新时不重复写库并反馈“模型目录已更新”，无变化时反馈“已是最新数据”。初次加载、检查、提交和主动刷新统一防重入，取消不当作失败。
- `ModelListScreen` 通过 Material 3 `PullToRefreshBox` 提供下拉刷新（空目录同样支持），指示器避开顶部系统栏，并为读屏提供“刷新目录”自定义操作。刷新期间只保留上方的下拉刷新指示器，不额外显示内联加载提醒。不再使用 Snackbar；`CatalogHeader` 与提醒动画容器合并在同一个稳定 key 的列表项内，提醒仍位于页头与排序说明／模型列表之间，随列表普通滚动，不固定、不吸顶，不主动滚回顶部。提醒上方的 16dp 间距包含在动画内容内，退出结束后高度与间距都归零，不因空列表项残留间距；阻断错误与空目录继续使用状态卡片。
- 首次无本地目录及首次失败重试在 `isLoading && models.isEmpty()` 时展示六个惰性模型骨架条目，包含 56dp logo、名称／LAB／标签、参数、模态与价格占位；圆角、背景、内边距及间距贴合真实卡片。骨架不创建假 `ModelSummary`，不改变数据源和 ViewModel；页头保留应用名与“模型目录”，不显示虚假的零模型数量。占位块沿用主题 `onSurface` 的低透明度底色与高亮，整组共用 1.2 秒循环的斜向扫光时钟，仅在绘制阶段读取动画值。应用非 `RESUMED`、切到设置页、打开说明子页面或设计预览时静态展示；系统关闭动画时同样保留静态骨架。第一条骨架提供礼貌级“正在加载模型目录…”与不确定进度语义，其余占位不提供重复读屏节点或操作。加载完成后直接显示真实列表／空目录／错误，不人为延迟；已有数据刷新继续保留真实模型与原下拉刷新指示器，不显示骨架。
- 查询区在首次骨架加载期间同样保留稳定 `catalog-query` 条目：`ModelCatalogControls` 接收可空 `skeletonProgress`，复用真实搜索框、筛选按钮及已恢复条件标签的布局；`modelSkeletonPlaceholder` 只替换绘制并清除占位读屏语义，各控件同步禁用输入与点击，不开放键盘或筛选面板。排序说明也保留等尺寸占位，并置于卡片之前。查询区、排序说明和卡片共用一个扫光时钟，加载完成直接恢复真实绘制与交互，不因新增查询区和排序行推动首张卡片；错误时移除控件占位，重试恢复。已有目录刷新继续显示可交互的真实控件，不使用骨架。
- `AnimatedCatalogStatusNotice` 以不含模型列表的 `CatalogNotice` 展示投影作为可空动画目标，使用 Compose `AnimatedContent` 淡入（200ms）／淡出（180ms）及 `SizeTransform` 高度过渡；出现、消失和错误／待更新／成功内容切换均平滑让位，不加弹跳或大幅滑动，遵循系统动画时长设置。退出内容保留到动画完成，但立即禁用旧操作并清除旧读屏语义，避免重复按钮和过时动作。刷新开始时只退出原提醒，不生成新的加载卡片；快速状态切换可中断过渡并收敛到最新状态。页头离开视口后不为恢复显示而重播入场动画，原有成功清除计时不受动画重建影响。
- 内联提醒使用 24dp 圆角、16dp 内边距、主题 `surfaceContainerLow` 背景，正文保持 `onSurface`，图标以 `primary`／`error` 区分状态，文本提供礼貌级读屏更新语义。操作独立排列并支持换行。刷新期间整体隐藏，结束后显示优先级为刷新错误 → 更新结果 → 待更新。“模型目录已更新／已是最新数据”仅显示图标与正文，不显示关闭按钮或空操作区，默认约 3 秒后自动清除，并遵循无障碍服务建议的显示时长。计时由 `ModelListScreen` 在列表外管理，避免条目滚动回收时重置；结果被更新中、错误或其他结果替换时取消旧计时，避免清除新的状态。待更新提示仍需显式确认；失败不自动消失，有待应用快照时继续提供“立即更新”，否则提供“重试”，并保留手动关闭。关闭错误仅清除消息，不丢弃待更新快照，随后恢复待更新提醒。
- `ModelListRoute` 负责获取 Hilt ViewModel 和生命周期感知的状态收集，不直接请求数据源。
- `ModelListScreen` 通过参数接收状态，不依赖 Hilt 或 ViewModel。修改时保留这一边界，使预览和后续界面测试可以使用独立数据。
- 查询条件由 `ModelListViewModel.setQuery()` 更新，在完整 LLM 列表上派生 `visibleModels`，不改变仓库、数据库、LLM 收录或报价选择。搜索去除首尾空白并按空白分词，不区分大小写，每个词须在名称、稳定 ID 或 LAB 中至少命中一处；保留原发布时间排序。不同筛选字段取交集，LAB 多选取并集，输入／输出模态分别要求所选项全部具备，未知模态保留原文；权重为全部／公开／未公开／暂无数据。最低上下文和最大输出门槛采用精确十进制 tokens（上下文预设 32,000／128,000／200,000／1,000,000，输出预设 4,000／16,000／32,000／128,000），包含等号，缺失数值不满足最低要求。门槛胶囊及已选标签使用精确 K／M 缩写（如 128K、1M；非整值 131,072 为 131.072K），不舍入阈值，读屏提供带千位分隔的完整 tokens。报价仅判断列表选定报价是否存在，不将零价当缺失，也不推断免费或服务可用。
- 搜索区参考胶囊式布局：无描边的填充搜索框、带图标与数量的筛选按钮，以及可逐个移除的已应用条件标签；旁边的“重置”仅清除筛选、保留搜索，搜索框可单独清空，无结果卡片可清除全部条件。搜索框和入口仍是页头提醒之后的普通列表项随内容滚动，不新增固定应用栏、排序功能、收藏或重复设置入口，沿用动态深浅主题，不复制参考图的固定橄榄色。
- 筛选面板为大圆角底部弹层，标题在上方，不提供关闭图标按钮，分组内容滚动，底部固定等宽“重置／应用”操作，应用按钮不显示条件数；选项使用浅色胶囊，选中项以主题色细描边及淡底强调，输入／输出模态附原有图标。普通字号面板约占可用高度 75%，大字号提高到 90%。打开时从已应用条件建立独立草稿，实时预览匹配数量，但点击“应用”前不修改列表或 ViewModel；面板内重置只修改草稿并保留搜索，点击遮罩、系统返回和滑动关闭放弃未应用修改。草稿及 LAB 展开状态通过 UI 的 `rememberSaveable` 恢复，不将草稿写入业务保存状态。LAB 默认显示至多八个选项，已选项始终可见，超过时可“更多／收起”；已选项超过八个则全部保留。
- `ModelCatalogControls.kt` 提供搜索／筛选入口、已选条件和筛选面板内容的独立深浅色预览，并覆盖窄屏双倍字号；面板预览直接复用内容组件，不依赖弹层窗口、ViewModel 或网络，合成样本仅供预览使用。
- LAB 筛选选项及已应用 LAB 标签采用 `LabFilterOrder.kt` 的混合排序：前八位固定为 `openai → anthropic → google → deepseek → alibaba → meta → xai → moonshotai`，其余 LAB（包括新出现的 LAB）按完整 LLM 目录的模型数量从多到少排列，同数量按原始 ID 字母序稳定兜底。仅展示目录或已选条件中实际存在的值，刷新后消失但仍选中的 LAB 按零个模型处理；计数使用 `uiState.models`，不随当前搜索或筛选结果缩小。不新增热度请求，也不代表模型能力排名或改变列表发布时间排序；面板收起时仍优先保留所有已选项，已选和未选组内分别沿用该顺序。
- 条件数将 LAB 多选计作一组、每个必需模态计作一个条件，其他字段各计一个。可选 LAB／模态从完整目录生成，不因当前结果缩小而消失，刷新后已选但消失的值仍可取消。活动查询下页头显示匹配数／总数；原目录为空仍显示空目录，原目录非空且无匹配才显示无结果，不能误触发首次加载骨架。
- `SavedStateHandle` 仅保存查询条件的 JSON 字符串，不保存目录或结果；默认／损坏的保存值回退到无条件。刷新、确认更新、失败及切换标签均保留条件，目录 Flow 变化重新匹配，不额外发起请求。列表位置和面板显示仍是 UI 可保存状态；离开模型页关闭面板并清除输入焦点。Activity 声明 `adjustResize`，模型页通过 Scaffold 外层单次 `imePadding()` 缩小键盘打开时的实际列表视口，已消费的 IME 安全区不再由 Scaffold 重复计算；其余系统栏／刘海安全区仍传给列表 `contentPadding`，搜索键收起键盘。
- `ModelAtlasApp` 承载两个标签：模型列表与设置。外壳通过内容插槽保持可独立预览和测试，真实模型页继续使用 `ModelListRoute`，不新增业务数据源。`rememberPagerState` 保存当前页面，两个页面都保留在分页器中，切换时不重建列表或主动清除已有状态提醒；成功结果的自动清除计时继续进行。列表位置和报价展开状态继续使用各自可保存状态。
- 双击悬浮导航的“模型”标签通过 `animateScrollToItem(0, 0)` 平滑滚回目录最顶部（首项、偏移均为 0），替代瞬间跳转；长列表沿用 Compose 的远距离滚动处理，不自行逐条执行动画。从设置页双击时，切页动画与列表回顶同时进行。`ModelAtlasScaffold` 通过 `rememberLazyListState` 保存列表滚动状态并沿内容插槽、Route 传给 Screen，置顶仅操作 UI 状态，不经过 ViewModel、不刷新数据，也不清除报价展开或状态提醒。单击和普通切页仍保留阅读位置。
- 导航以不消费事件的指针监听按系统双击时间阈值识别模型标签双击；单击继续由 `selectable` 立即处理，不等待双击超时。拖动、多指、长按、越界或点击设置会中断连续模型点击，保留 Tab 单选语义与既有横向拖动切页；设置标签不提供双击操作。模型标签另提供“回到模型列表顶部”自定义无障碍操作，避免要求读屏用户执行触摸双击手势。
- 设置入口打开全屏 `DataSourceScreen`，替代原来源许可弹窗并集中展示原列表底部的价格说明，不依赖目录是否已加载。外壳以 `rememberSaveable` 保存子页面显示状态；打开时主分页器和玻璃导航保持组合但不放置，避免绘制、触摸与读屏穿透，同时保留列表位置、报价展开和提醒计时。页面提供固定在系统安全区内的返回按钮，系统返回由 `NavigationBackHandler` 处理，两种方式都回到设置页。说明正文使用 `LazyColumn`，支持滚动位置恢复、深浅主题、大字体与 RTL；不引入通用导航栈或新数据源。
- 悬浮栏参考 RvSystem Monitor 的玻璃样式，使用 `io.github.kyant0:backdrop:2.0.0`：分页内容通过 `layerBackdrop` 记录，导航外壳和选中块分别采样，组合增艳、4dp 模糊、镜片折射与色散，覆盖主题半透明底色。Android 12/12L 支持模糊，Android 13 及以上再叠加运行时着色器折射；Android 10/11 与设计预览使用实色胶囊兜底。没有新增外观开关，配色沿用动态主题。
- 胶囊底部居中，距系统导航安全区 16dp，水平至少留白 16dp、最大宽度 400dp；底部与两侧避让导航栏及显示缺口。页面仍自行处理系统安全区，外壳只额外传递实测导航高度加 32dp 外边距，列表使用底部 `contentPadding`，因此滚动内容可经过玻璃后方，而列表末尾及内联提醒的操作仍可滚动到导航上方。标签提供 Tab 单选语义及至少 56dp 的布局高度，字号放大时导航自动增高；位置与拖动映射支持 RTL。
- 模型标签使用三柱圆角图标：参考所提供 SVG 的 473∶223 比例、柱宽、高度与圆角，等比居中于 24dp 图标槽；中心柱更高更宽，两侧以 alpha 保留层次。未选中使用 `ic_nav_models_outline.xml` 中空描边版，选中使用 `ic_nav_models.xml` 实心版；描边约 1.3dp，中心线向内收缩，保证两版外轮廓一致，内部真正透明而非背景色填充。图标通过既有 Crossfade 过渡，颜色沿用导航主题，不固定 SVG 的灰黑色；列表状态卡片的 `ic_catalog` 保持不变。
- 页面不使用 `TopAppBar`、Scaffold 的 `topBar` 插槽或应用栏专用滚动行为。模型列表的标题与目录数量放在 Scaffold 内容区内，作为 `LazyColumn` 的首项 `CatalogHeader`，与模型卡片同速滚动，回到列表顶部时才再次显示，不固定、不吸顶，也不在下滑时单独弹出。Scaffold 最后叠加随主题变化的半透明状态栏渐变保护层，不占布局空间、不拦截触摸。来源许可入口已移至设置页，模型列表不再显示价格说明卡片。
- 列表使用 `models.json` 字典键作为稳定 `id` 和条目 key；每个规范模型一行。同名模型不合并，缺少服务商关联的规范模型仍展示；查询仍保留显式 `canonical_model_id` 左连接，摘要映射按规范模型 ID 去重，不再向界面传递服务商和描述。报价来自独立的 `observeOfferingPriceRows()` 投影，两条 Flow 在仓库层用 `combine` 合并后一起映射，因此规范模型元数据与报价不会互相污染。
- 数据库存储整个目录以保留未关联服务和完整报价；列表收录明确 `type = "llm"`，或无类型且输入、输出模态均包含 `text` 的记录。明确非 LLM、未知类型及无法判断的记录不收录。该规则不是模型能力认证。

## 当前实现边界

- 列表展示模型名、LAB 文字与 logo、发布时间、上下文长度、最大输出、多模态能力和开放权重。LAB 取稳定 ID 第一个 `/` 前的非空白部分，没有分隔符或有效前缀时显示“暂无数据”，不视为独立核实的开发商。日期保留来源精度，限制在数据层保留完整 `Long`，列表视觉使用十进制 K／M 缩写、最多一位小数并去除末尾零（`1,000,000 → 1M`、`384,000 → 384K`、`131,072 → 131.1K`）；读屏仍提供字段名、带千位分隔的精确值和 tokens 单位。上下文长度使用横向扩展图标（`ic_context_window`），最大输出使用文字输出箭头图标（`ic_output_limit`），均为随主题着色的 16dp 描边图标，视觉上不重复单位。
- 列表保留参考 RvSystem Monitor 的圆角结构，模型卡片仅按新的参考图调整紧凑布局、不复制配色：24dp 圆角、16dp 内边距与 16dp 列表间距；56dp logo 右侧显示模型名、LAB、合并的权重许可标签，下面是可换行的图标参数行；输入与输出模态标签横排，中间以指向输出的箭头区分，不显示“输出：”前缀，读屏保留输入／输出说明，窄屏可换行。模型能力与价格之间仅用一条细分隔线分区，价格不再嵌套独立背景或圆角卡片；输入／输出及存在时的缓存命中输入仍以主题色突出、列间显示细分隔线，按价格区可用宽度和字号调整为 1–3 列，窄屏时不额外缩小字体、截断单价或将价格改为近似值。保留顶部目录数量与既有发布时间倒序；点击卡片打开详情，价格档位标题独立处理展开，不触发页面跳转。
- 主题仍使用现有 Material 3 依赖；Android 12 及以上使用系统动态浅色／深色调色板，低版本使用固定备用色。预览关闭动态取色，保持可复现；测试可通过 `ModelAtlasTheme(dynamicColor = false)` 固定配色。加载、空目录、分类错误和来源说明区块统一采用圆角与色调分层。Activity 的 `enableEdgeToEdge()` 继续管理系统栏图标颜色，保留时间、电量等系统信息。模型列表合并系统栏与显示缺口 insets，Scaffold 将安全区传给列表的 `contentPadding`，不在列表或其父容器添加系统栏 padding，因此初始标题和首条内容避开系统图标，滚动内容仍可延伸到状态栏下方；底部安全区不变。来源说明子页面则整体避让安全区，确保固定返回按钮和滚动正文不被系统栏覆盖。
- 开放权重按 `open_weights` 三态展示：“开放权重”“权重未公开”“开放权重：暂无数据”；仅在权重公开且 `license` 去除首尾空白后为 1–16 个字符时，以中点附带原始许可短名，如“开放权重 · MIT”“开放权重 · Apache-2.0”。长名称不截断、不猜测简称，列表仅显示“开放权重”，详情单独展示完整许可原文。该字段是来源报告的权重可获取状态，不等于开源、允许任意用途或允许商用，界面来源说明已注明这一点。
- 列表在模型名左侧显示 LAB 彩色 logo：素材来自 Models.dev 的 Logos 服务，随包覆盖 23 个实验室，按稳定 ID 第一个 `/` 前的前缀精确匹配；没有素材（含 LAB 缺失）时显示兜底星光图，不借用其他实验室的素材，也不从服务商 ID 或名称反推对应关系。素材保留品牌原色、不随主题变色，因此使用固定白色圆角底，保证深色图案在深色卡片上同样可读；logo 只是视觉标识，「LAB 为 ID 前缀标签、未经独立核实」的说明保留。
- LAB 在界面文案中统一称为“研发机构”，包括搜索提示、筛选分组、已选标签、读屏与来源说明；仍保留按模型 ID 前缀展示、未经独立核实的说明。机构名称通过 `LabDisplayName.kt` 将已收录的 23 个前缀映射为品牌惯用写法（如 `openai → OpenAI`、`deepseek → DeepSeek`、`xai → xAI`），模型卡片、筛选选项、已选标签及其读屏使用同一名称；未知 LAB 保留原文，缺失值仍显示“暂无数据”。只调整展示，`ModelSummary.lab`、查询条件、搜索匹配、LAB 排序和 logo 匹配仍使用原始 ID，不改数据库或服务商认定。
- 列表顺序由映射层固定按发布时间倒序（最新在前）：缺失或空白发布时间的模型排在最后，发布时间相同再按名称（不区分大小写）和稳定 ID 稳定排序。日期只按字符串字典序比较，不解析或补全，因此 `2025` 早于 `2025-06`、`2025-06` 早于 `2025-06-01`；DAO 仍按名称排序，仅作为去重前的稳定输入。当前不提供排序切换。
- 多模态能力分别展示输入和输出，标签仅显示图标、不显示文字，读屏保留模态描述：已知模态翻译为中文，未知模态保留原值；缺失描述为“暂无数据”，显式空集合描述为“无”。文本模态使用 16dp 的 T 形描边图标（`ic_text`），随主题着色；PDF 保留文档图标。列表字段仅取规范模型，不混用 API 服务商限制；服务商和摘要仍保留在本地目录。仅预览和测试保留合成样本，运行时没有 demo 数据。
- 卡片展示一条报价：以「API 价格 · 美元 / 1M tokens」为小节标题，分列展示输入、输出及存在时的缓存命中输入单价，逐行展示来源提供的缓存写入、推理、音频输入、音频输出单价；单位统一为 USD / 百万 tokens。价格区直接沿用外层模型卡片的 `surface` 背景，标题和价格使用 `primary`，权重公开标签使用 `tertiaryContainer` / `onTertiaryContainer`，跟随系统动态浅色／深色调色板，不硬编码参考图中的绿色。界面不展示服务商名称，也不标注“官方／第三方”或数据未提供的“非高峰”，选取规则只在 `PriceProviderPolicy` 与 `selectPriceFor` 内部决定展示哪一家，该规则是应用内的展示顺序，不是质量或最低价排行榜。没有可用报价时显示「价格：暂无数据」，不补造零价；明确的 `0` 显示为 `$0.00`。`tiers` 的上下文档位默认折叠，有档位时标题行显示箭头并可点击展开／收起，读屏提供动作与状态；无档位时不显示无效箭头。设置中的「数据来源与许可」独立页面注明同一模型的不同服务商价格不可直接比较，列表不再重复说明。
- 价格格式化至少保留两位小数（`0.6` → `$0.60`），来源更精确时保留更多位（`0.003` → `$0.003`），避免把高精度报价抹成 `$0.00`。
- 价格区采用无独立背景的 `PriceSection`，取消内层圆角容器与额外内边距，标题、指标与分隔线左对齐；标题与计价单位统一采用 `labelSmall`（默认 11sp／16sp 行高），弱化说明文字、突出 `titleMedium` 单价，保留系统字号缩放。标题箭头缩至 16dp，不再设置 48dp 的布局最小高度，默认标题行随内容约为 16dp；`clickable` 在布局外扩展触摸区域，维持至少 48dp 的触摸高度，附近无其他可点击控件。标题与指标等区域间距 4dp，指标内部间距 2dp，换行间距 8dp。仍保留输入／输出／缓存分列、完整价格精度、单位及其他类别；档位门槛显示完整数值，不使用 K／M 近似。
- 档位展开箭头遵循折叠区惯例：收起时朝下，展开后朝上，通过 0°／180° 旋转动画过渡；不使用目录树式的朝右／朝下组合。整行点击和读屏动作／状态不变，UI 回归比较箭头实际绘制的尖端方向，覆盖展开与再次收起。
- 已实现本地优先与离线浏览：每个新的列表 ViewModel 有本地目录时后台检查一次，没有时自动加载；不轮询、不增加 WorkManager 或退出后的后台任务。仅比较完整规范模型的稳定 ID 集合来决定是否提示增删（同数量替换也提示），不受 LLM 收录结果、搜索或筛选影响。ID 集合不变时，参数、名称、类型、报价以及服务商／模型服务的增删改均静默以完整快照事务同步，列表随 Flow 更新，查询条件保留并重新匹配；不会把内容变化误当成模型增删。不将本地抓取时间或字典顺序变化当作内容更新，内容一致不重复写库。模型增删的待应用完整快照仅暂存在仓库内存中，进程重启后重新检查。目录页尚未展示统一更新时间，也未引入 TTL 策略；详情只观察本地，不额外检查网络。
- 两端点均成功解析后才事务提交；失败或取消不会写入半份目录。HTTP 429 / 503 尊重 `Retry-After`（秒数或 HTTP 日期），缺失或非法时冷却 60 秒。OkHttp 不自动重试请求。
- 页面提供来源与第三方说明、随包 MIT 许可全文；[AndroidManifest.xml](../app/src/main/AndroidManifest.xml) 已声明普通 `INTERNET` 权限，不申请运行时危险权限。
- 当前有模型与设置两个顶层标签、模型详情与来源许可说明子页面；没有收藏、对比或基于路由的通用导航栈，不因导航入口存在就假定对应业务能力已实现。
- Models.dev 的收录、计价、能力与许可约定见[数据源说明](data-source.md)。

## 模型详情的数据与导航

- `CatalogDao.observeModelDetail(id)` 在单个事务内通过嵌套 Relation 读取规范模型、所有 `canonicalModelId = id` 的模型服务和服务商；不使用只含有价服务的列表报价投影，不按名称推断关联。`DefaultModelRepository.observeModelDetail()` 在 IO dispatcher 映射并去除相同值，遵循列表相同的 LLM 收录规则。模型不存在／不再符合规则返回 null；空服务列表仍是有效详情。没有 Schema 修改或网络端点新增。
- `ModelDetailMapper` 将全部服务分组，第一方服务商优先、第三方按已有优先级及 ID 兜底；同服务商的服务按稳定 ID 排序，不筛选最低价。规范模型与服务分别保留元数据，完整保存七类价格、精确档位和独立模式价到应用模型，不计算费用或合并模式，不展示／执行请求 body、headers 等配置。规范模型已有的 `benchmarks` 映射为独立 `ModelBenchmark` 应用模型，保留缺失／空列表、来源顺序、重复名称、全部元数据和分数原值，不变更数据库或新增网络请求。
- `ModelDetailViewModel` 的 `open(id)` 订阅本地 Flow，首个结果到达前保持加载；null 显示不存在／已移除，读取异常显示可重试错误，取消不当作错误。`retry()` 只重新订阅本地查询，不联网、不提交待更新目录。Route 进入时打开、退出时关闭，切换模型取消旧订阅，避免 Activity 级 Hilt ViewModel 累积全部已浏览模型。详情随目录提交更新，模型被移除后不继续显示旧内容。
- 外壳通过 `rememberSaveable` 保存选中的稳定模型 ID，回调沿 Scaffold → Route → Screen → Card 传递；`SharedTransitionLayout` 统一承载列表与详情。`ModelContainerKey(modelId)` 匹配 `ModelCard` 外层与外壳的 `ModelDetailContainer`，使用 `sharedBounds` 在约 450ms 内将整卡扩展到全屏／缩回原卡片。内部 `ModelSharedElementKey(modelId, role)` 与容器键隔离：Logo 使用 `sharedElement`，名称／机构／权重使用 `sharedBounds` 及 `scaleToBounds`，与容器同为 450ms；身份在根覆盖层的层级为 2，高于卡片容器的 1 和详情容器的 0，默认继承最近父容器的动态裁剪路径，不叠加父级缩放与局部内容淡化，也不留两份身份。外层 `scaleToBounds(ContentScale.FillWidth, Alignment.TopStart)` 保持最终布局与等比缩放，避免逐帧文字重排或非等比拉伸；默认内容占位不影响其他列表项。两端使用同一扩展进度驱动根覆盖层圆角（24dp → 0dp），详情不透明表面在绘制阶段插值 `surface` → `surfaceContainerLowest`，其 Scaffold 在宿主中透明、独立预览仍保留背景；其他卡片及详情内容以 120ms 局部淡化交接，背景目录和悬浮导航不再整页淡出。可见性使用公开的 `Transition<EnterExitState>` 实现保留内容的 `AnimatedVisibilityScope`，不让通用页面切换容器销毁主分页器；动画期间两页参与放置，结束后隐藏主分页器但继续组合。退出页面立即清除可访问语义并阻挡触摸，列表不可见时暂停骨架、清除焦点／筛选面板。复用现有 `NavigationBackHandler` 统一处理说明／详情返回，不引入 Navigation 3 或通用导航栈。返回保留目录、查询、滚动、价格展开与提醒计时；详情退出动画结束后才移除组合并取消本地订阅，动画中再次返回不会退出应用。详情按模型 ID 创建 UI 状态，正文位置、服务参数与档位展开可恢复，不把目录对象写入保存状态。
- 点击卡片时仅捕获 `ModelIdentitySnapshot`（ID、名称、LAB、权重状态和许可）到外壳内存，经动画作用域供详情的加载头部使用，不构造假的 `ModelDetail`、不写入 ViewModel 或保存状态、不阻塞跳转等待读库。容器目标挂在稳定外壳上，不随加载／真实内容切换重建；加载和内容头部使用一致的布局起点，真实 Flow 到达后原位接管身份，错误／不存在状态不保留快照头部。恢复导航只保存 ID，不恢复或重播快照。详情公共头部有任何纵向滚动时停用身份共享匹配，但外层容器仍缩回原卡片，不强制恢复任一端滚动位置；来源卡片消失或无可见容器匹配时才使用 180ms 页面淡出兜底。快速返回可中断动画，系统关闭动画时立即完成布局和清理；预测性返回进度联动仍未实现。
- 详情 Screen 不依赖 Hilt；分为“概览／价格／评测”三个标签，默认概览。概览由 `overviewItems` 依次组织能力、技术参数、权重许可、相关资料和来源时效五个独立惰性条目。价格内部保留 `PROVIDERS` 稳定 UI 状态名与全部关联服务，不筛掉无价服务；常规宽度标签等分，窄屏或大字体空间不足时使用 `PrimaryScrollableTabRow`。返回栏位于列表外固定显示，身份区、参数卡与标签栏仍为 `LazyColumn` 的普通滚动内容。三份 `rememberLazyListState` 留在切换内容外独立保存滚动位置；服务参数与各基础／模式档位的展开键集合由页面 `rememberSaveable` 保存，不依赖离屏条目的组合生命周期，避免恢复时因折叠内容变短而夹回位置；`rememberSaveableStateHolder` 另按标签保留链接错误等局部状态。所选标签可恢复，状态列表使用独立状态，异步读取不夹回正文位置。概览的技术参数展示精确 tokens，完整许可、模态文字和能力三态均可换行。单价保留原精度，按窗口宽度和字号使用 1–3 列；服务限制与来源默认折叠，档位门槛不自行解释等号归属。外链只允许具有主机且不带用户信息的 HTTP(S)，非法资料链接禁用，无法打开时提供行内错误。评测来源只有安全 HTTP(S) 可点击，其他出处保留文字；不生成排名或推断分数方向／百分比。
- 正文与加载／错误／不存在状态分别持有列表滚动状态，避免进程恢复后异步读库期间的单条加载列表把正文位置夹回顶部。筛选面板的草稿及机构展开状态也保存在弹层窗口外的页面保存状态域中，内容组件只接收值与回调；弹层关闭后丢弃草稿，不影响已应用条件。
- 详情的外层 `LazyColumn` 只保留一份公共身份区、参数卡和标签栏，最后一个条目中的 `HorizontalPager` 仅承载标签下方的 `DetailSection` 正文。正文可左右滑动切换，点击公共标签通过 `animateScrollToPage()` 动画切页；头部与标签栏不参与横移，也不因切页改变公共纵向位置。`rememberPagerState` 作为当前页与选中标签的唯一来源并保存恢复，RTL 下由分页器处理方向。分页器按返回栏下的可用视口限定高度，各页继续使用独立惰性列表；`nestedScroll` 上滑时先消费外层剩余滚动，再滚正文，下滑时正文到顶后交还外层，身份区及标签栏仍可滚出视口，不吸顶。每页以标签名为稳定 key，正文绑定不可变标签，离屏页清除读屏语义；公共列表状态、三份正文列表状态、展开集合及局部状态容器均保留在分页器外，加载／错误／不存在使用独立列表。UI 回归覆盖拖动中公共区域位置不变、身份区横滑不切页、上下滚动交接、首末页边界、点击与滑动联动、RTL，以及阅读位置／展开状态／异步恢复。
- 详情首条为 `ModelDetailHeader`：88dp 居中 logo／名称／机构与许可、左对齐描述、居中方向模态与明确支持的能力标签，接独立 `surface` 参数卡；页头直接沿用页面背景，不绘制彩色渐变，不测量卡高或背景交界。三个标签共用一份页头及其纵向位置，仅正文各自保存滚动。`ModelIdentity(centered = true)` 与 `ModelCoreMetrics(prominent = true)` 复用列表素材、样式与缩写，读屏提供精确值，概览的技术参数分区保留完整数值；服务参数仍使用自身完整元数据。顶部安全区由列表外的 `DetailHeaderBar` 避让，返回栏两侧及公共列表侧边避让系统栏和显示缺口，底部安全区放在各正文列表的内容留白中，父容器统一消费 insets；列表实际视口从返回栏下方开始，滚动定位不被遮挡。整页随容器在根覆盖层中等比缩放，Logo 保持比例，名称的大标题与紧凑返回栏的滚动交接仍独立工作。
- 返回栏初始显示品牌名，大标题距列表顶部 24dp 时开始交接，完全离开视口后显示紧凑模型名；根据标题相对首条的实测边界和公共外层 `LazyListState` 派生连续进度，描述长度不影响触发位置，切换标签不改变标题进度。淡化及最多 8dp 位移在 `graphicsLayer` 读取进度，两层栏标题始终参与测量，过渡不改变栏高；没有独立计时动画，反向滚动和恢复位置直接反映当前进度。正文大标题与紧凑标题在半程切换无障碍可见性，紧凑标题单行省略但保留完整读屏名称。加载／失败／不存在时仅显示品牌与返回操作。
- 详情显示规范模型及各服务的上游条目更新时间、本地获取时间，获取时间不是价格生效时间；与现有写库规则一致，相同内容未再次写库时保留原获取时间。目录页统一更新时间仍未实现。

## 现有单元测试

测试源码位于 [`app/src/test/java/io/github/chenlongapps/modelatlas/`](../app/src/test/java/io/github/chenlongapps/modelatlas/)。

| 测试 | 已覆盖内容 |
| --- | --- |
| [`ModelDetailMapperTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/mapper/ModelDetailMapperTest.kt) | 完整元数据、显式关联、保留所有服务、分组稳定顺序、缺失／空／零值、全部档位类别、独立模式、完整许可与链接、LLM 收录规则，以及 Benchmark 全字段、原始分数、重复名称和来源顺序 |
| [`ModelDetailViewModelTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modeldetail/ModelDetailViewModelTest.kt)、[`DetailLinkTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modeldetail/DetailLinkTest.kt) | 只读本地订阅、动态内容与移除、读取失败重试、防重复订阅、切换／关闭取消与重新打开、取消不报错，以及外链协议／主机／用户信息校验 |
| [`ModelRepositoryTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/repository/ModelRepositoryTest.kt) | 完整写入、失败不写半份、取消、数据库 Flow、空目录、限流冷却、仅模型增删暂存且不写库、内容／服务商／报价变化静默提交、清除过期待更新快照、应用不重复下载、无变化不写库、手动刷新取代暂存快照与提交失败可重试 |
| [`ModelListViewModelTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListViewModelTest.kt) | 首次加载与重试、提交后查询结果延迟时保持加载且不发布瞬态空目录、真实空结果正常结束加载、本地优先、后台检查、静默更新无提醒且重新匹配查询、主动应用、刷新失败保留数据、检查中下拉复用请求并按三种结果反馈、刷新去重与取消，不对外暴露 `MutableStateFlow` |
| [`ModelListQueryTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListQueryTest.kt) | 名称／ID／LAB 搜索、多词与大小写、组合条件、方向模态、权重三态、缺失值与零报价、Long 门槛边界、保序、独立重置与空目录／无结果区别 |
| [`CatalogStatusNoticeTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/CatalogStatusNoticeTest.kt) | 内联提醒的显示条件、无状态与刷新中不占位、首次加载与阻断错误不重复提醒、关闭结果保留待更新提示，以及动画目标不受模型列表变化影响 |
| [`CatalogSnapshotTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/local/CatalogSnapshotTest.kt) | 规范模型 ID 集合增删／同数量替换与内容变化分离、完整内容比较、忽略抓取时间与字典顺序、增删及参数／报价变化、保留缺失与空集合／零值区别 |
| [`ModelsDevApiTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/remote/ModelsDevApiTest.kt) | MockWebServer 验证真实 Retrofit 路径、`type=all`、两个端点的解析与 HTTP 错误不自动重试 |
| [`CatalogMapperTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/mapper/CatalogMapperTest.kt)、[`ModelListMapperTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/mapper/ModelListMapperTest.kt) | 独立 ID、显式关联、完整复杂字段、缺失信息、规范模型去重、列表元数据映射、开放权重与许可三态、发布时间倒序、报价投影挂载与未匹配报价行 |
| [`ModelPriceMapperTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/mapper/ModelPriceMapperTest.kt) | 官方优先、第三方优先级、同服务商取最低、零价与缺失价、纯档位报价、无服务商名称、缓存／推理／音频与旧版字段 |
| [`PriceProviderPolicyTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/model/PriceProviderPolicyTest.kt) | 已核实的第一方对应表、无官方对应的 LAB、第三方优先级顺序与未列入者排序 |
| [`ModelSummaryTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/model/ModelSummaryTest.kt) | LAB 的首段前缀提取、无分隔符与空白前缀边界 |
| [`LabLogoTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/LabLogoTest.kt) | 23 个已收录 LAB 的素材映射、未知与空白 LAB 不借用其他素材、缺失 LAB 使用兜底图、LAB 取稳定 ID 前缀 |
| [`LabDisplayNameTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/LabDisplayNameTest.kt) | 23 个 LAB 的品牌名称映射、未知与已格式化值保留原文、显示名称不改变原始 ID 与筛选／大小写搜索 |
| [`LabFilterOrderTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/LabFilterOrderTest.kt) | 前八位固定优先级、其余按模型数量降序、同数量字母序与缺失计数兜底、完整目录 LAB 计数、已选子集排序与不修改原条件、空集合与单项边界 |
| [`ModelPriceFormatTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelPriceFormatTest.kt) | 零价、至少两位小数、高精度小额报价不被舍入为零 |
| [`ModelTokenFormatTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelTokenFormatTest.kt) | 列表 K／M 缩写、一位小数与单位进位边界、零值与 Long 极值，以及读屏和档位使用的精确千位分隔格式 |
| [`ModelLicenseFormatTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelLicenseFormatTest.kt) | 保留原始许可短名、首尾空白、16 字符边界及空白／长名称不展示，不推测许可简称 |
| [`LlmInclusionPolicyTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/model/LlmInclusionPolicyTest.kt) | LLM、多模态、非 LLM、未知类型与缺失模态的收录边界 |
| [`ModelsDevParserTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/remote/ModelsDevParserTest.kt) | 两种响应的字段、嵌套结构、标识、缺失与空值、未知字段和错误响应 |
| [`InterleavedDtoSerializerTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/remote/InterleavedDtoSerializerTest.kt) | `interleaved` 的布尔／对象形式及空值往返序列化、未知字段和非法类型 |
| [`CatalogTypeConvertersTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/local/converter/CatalogTypeConvertersTest.kt) | 全部本地 JSON 转换器的空值与往返转换、完整报价、复杂模式、开放字符串、未知字段和错误类型 |
| [`CatalogEntitiesTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/local/entity/CatalogEntitiesTest.kt) | 本地实体的默认未知值、独立标识、显式获取时间及模型／服务元数据边界 |

测试目录另有仅供 Room KSP 编译检查的 [`CatalogSchemaTestDatabase`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/local/CatalogSchemaTestDatabase.kt)，不创建运行时数据库。异步单元测试使用 `kotlinx-coroutines-test` 和手写 fake，不依赖公网。

查询相关的 `ModelListViewModelTest` 还覆盖条件修改不增加网络请求、首次加载前设置条件、目录更新重新匹配、刷新成功／失败保留查询与数据、条件完整保存恢复及损坏保存值兜底。`ModelFilterFormatTest` 验证预设缩写和非整值／Long 极值不丢精度。`androidTest` 的 `ModelCatalogControlsTest` 覆盖搜索与匹配数量、独立清空搜索、无结果重置、空目录区别、LAB 多选与方向模态组合、未知模态、草稿预览数量、确认前不提交、关闭及系统返回丢弃草稿、重置保留搜索、已应用标签单独移除及独立重置、LAB 更多／收起保留选中项、深色大字号下滚动到数值和报价条件、刷新后仍可取消消失的选项，以及切页和面板草稿恢复；业务条件的恢复另由 ViewModel 保存状态测试验证。

`androidTest` 中的 `CatalogDatabaseTest` 使用实际 SQLite 验证关联、复合服务 ID、事务回滚、快照替换、文件重开、报价投影只取显式关联且有报价的服务以及未匹配报价行不会变成模型；`ModelListScreenTest` 使用 Compose Test APIs v2 验证真实字段、方向模态标签、缺失值、开放权重与短许可展示（长许可隐藏）、加载、空目录、重试，以及报价不显示官方／第三方标记、输入输出单价与单位、零价与缺失价、价格明细展开收起与状态恢复、档位标题至少 48dp 触摸高度，还包括 LAB logo 及兜底图、目录数量、Token 缩写与精确读屏语义、双倍字号下长名称和缩写数值不溢出；另确认列表不再出现来源按钮或价格说明。`ModelAtlasThemeTest` 验证固定与系统动态浅色／深色调色板，另有 API 30 及以下的备用配色用例。构建与测试命令统一见 [AGENTS.md](../AGENTS.md)。

详情相关 `androidTest` 的 `ModelDetailScreenTest` 覆盖状态／重试、精确参数、能力三态、完整许可、多个服务商与所有模型服务、独立模式、零价与高精度报价、精确档位和展开恢复、恢复后异步加载不夹回顶部、无效／无法打开外链，以及深色窄屏双倍字号 RTL 的长文本无裁切与末尾可达；另覆盖返回栏交接为模型名后固定可用、滚动交接中间帧及反向还原的像素、栏高与列表视口稳定、标签栏滚走、标签切换／选择／滚动与展开恢复、评测原值及完整元数据、安全出处和缺失／空列表，以及窄屏大字号评测标签和长字段。`ModelDetailNavigationTest` 覆盖卡片入口、主界面隐藏、按钮／系统返回与列表位置保持、详情 ID／滚动恢复及列表档位操作不跳转。`CatalogDatabaseTest` 另验证详情嵌套事务查询包含所有显式关联服务（含无报价），无关联模型保留及模型移除后返回 null。

`ModelContainerKeyTest` 与 `ModelSharedElementKeyTest` 单元测试检查容器／身份键隔离、规范模型 ID 与身份角色匹配，同机构模型不会误匹配；`ModelSharedTransitionTest` 使用根覆盖层实际变化像素验证整卡进出中间帧的宽高、位置、圆角裁剪及背景目录不淡化，同时检查四类身份共享节点、Logo 的移动放大与容器进度一致、不叠加父级缩放，以及最终卡片回位。另检查宿主中透明 Scaffold 的浅深色标题前景、退出结束才销毁详情、主列表始终保留组合且提醒计时不中断、加载到真实身份不跳动（含入场过程中完成读库且容器及 Logo 不重新展开）、错误清除快照、模型及来源卡片移除、入场中返回、详情滚动后停用身份匹配但仍收缩整页、同机构模型不串匹配及深色双倍字号 RTL 恢复。`ModelSharedTransitionReducedMotionTest` 以零动画时长检查双向收敛与详情清理；它们描述测试覆盖，具体执行结果以当次验证为准。

页头回归还检查三个标签均保留居中身份、描述、短许可和带精确读屏语义的参数缩写，logo／名称／机构的纵向顺序及居中对齐，页头和独立参数卡先于标签栏，未知模态／显式空集合、零值和权重三态不混淆。浅色／深色像素检查页头与正文背景一致、普通字号三列、返回与末尾内容避让系统栏；能力标签仅显示明确支持，不推断“长上下文”。概览内验证能力三态、技术参数精确数值、完整许可及异步恢复，窄屏双倍字号检查三个可滚动标签的完整文字。测试通过标签语义定位，不依赖标签在列表中的固定索引。

状态栏回归用例通过 `ModelListScreen` 的 `windowInsets` 参数固定顶部高刘海与底部安全区，覆盖浅色、深色及双倍字号；除检查初始内容和最后一张模型卡片避开安全区、标题随列表滚走及回到顶部后恢复外，还比较状态栏区域的实际绘制像素，避免仅验证布局边界而漏掉实色背景遮挡。另有保持手指按下的拖动用例，检查标题的实际位移，并确认页头与模型卡片同速滚动；滚动到列表中部后下滑不会单独显示页头。

价格区的浅色／深色回归用例比较价格区与外层模型卡片的实际背景像素，并检查标题、价格区与能力分隔线左对齐，防止重新引入内层卡片底色或额外缩进。

刷新回归包括实际下拉手势（有数据与空目录）、更新提示的显式确认操作、刷新失败仍显示本地模型及数量；数据库回归包括完整快照读取与目录存在性查询。`androidTest` 中的 `CatalogStatusNoticeTest` 覆盖浅色／深色下各提醒状态、礼貌级读屏语义、刷新期间无内联提醒且仅有一个加载指示器、失败重试与手动关闭、关闭错误保留待更新操作、成功结果约 3 秒后自动消失且无操作区、无障碍延长时长、旧计时不清除新错误或更新中状态、结果离开视口后仍自动清除，以及 320dp 窄屏双倍字号下正文和操作不裁切；列表回归检查提醒位于页头下、排序说明前，待更新提醒随列表滚动消失并在回到顶部后恢复，以及后台更新提示到达时不改变用户当前阅读位置。

`CatalogNoticeAnimationTest` 通过手动推进动画时钟，检查浅色／深色入场与退出的中间高度、后续模型的连续位移和实际背景像素淡化，退出后不残留间距；另覆盖错误切换回待更新时的高度过渡与旧读屏内容清理，以及刷新／成功结果快速替换退出动画。`CatalogNoticeReducedMotionTest` 以零动画时长验证直接应用最终布局。

`ModelCardSkeletonTest` 覆盖浅色／深色及 320dp 双倍字号下扫光实际像素变化、卡片背景不被扫光覆盖、唯一加载语义、加载到真实列表／错误与重试切换、已有目录刷新不显示骨架，以及隐藏页暂停与恢复动画。`ModelCardSkeletonReducedMotionTest` 使用零动画时长验证静态占位与加载语义。

`ModelAtlasAppTest` 覆盖真实列表与设置的标签切换、页面滑动与导航拖动、选中块对齐、切换后列表位置不变、标签与报价展开状态恢复、列表末尾避让悬浮栏、页头下更新提醒在标签切换后保留且可确认，以及双倍字号和 RTL 标签布局；另覆盖设置进入独立说明页、原主界面隐藏、价格说明仅显示一份、MIT 全文、按钮及系统返回、子页面与滚动状态恢复，以及深色双倍字号 RTL 正文无裁切。

导航置顶回归使用实际触摸双击，覆盖精确回到页头初始位置、顶部重复双击不刷新、从设置页双击返回并置顶、单击及超时的两次点击保留位置、设置双击不置顶、点击设置中断连续模型点击、导航拖动后点击不误触、列表位置恢复后仍可置顶、报价展开保留、自定义无障碍操作，以及深色双倍字号 RTL 下置顶与选中块对齐。

回顶动画回归手动推进动画时钟，检查普通距离与第 300 项返回时存在中间滚动位置、动画确实进行中，结束后首项与偏移精确归零，避免只验证最终位置而重新引入瞬移。
