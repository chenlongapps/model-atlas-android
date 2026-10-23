# 仓库说明

## 文档阅读指引

- 开始任务前先阅读 [文档索引](docs/README.md)，再按任务选择相关文档，不必一次读取全部文档。
- 修改应用结构、界面状态或数据流前，阅读 [架构说明](docs/architecture.md)；涉及 Models.dev、字段映射或计价时，阅读 [数据源说明](docs/data-source.md)。
- 必要的项目背景文档统一放在 `docs/`，使用中文并区分已实现与规划内容。构建规范保留在本文件，任务进度保留在 [TODO.md](TODO.md)；实现变化时同步更新相关文档，避免重复维护。

## 构建与验证

- 项目只有一个模块：`:app`。使用 `./gradlew`；虽然 Java 源码和目标兼容性均为 17，但 Gradle 守护进程需要 **JDK 25**（见 `gradle/gradle-daemon-jvm.properties`）。编译和目标 SDK 均为 **37**。
- 依赖和插件的版本统一定义在 `gradle/libs.versions.toml` 中。AGP 9 内置 Kotlin 支持：不要添加 `org.jetbrains.kotlin.android`。Hilt 和 Room3 的处理器使用 **KSP**，而非 kapt；Room 依赖使用 `androidx.room3`，而非 `androidx.room`。Room3 列转换使用 `@ColumnTypeConverter` / `@ColumnTypeConverters`，而非 Room2 的注解。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests 'io.github.chenlongapps.modelatlas.ui.modellist.ModelListViewModelTest'
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest # 需要 API 29 及以上的设备或模拟器
```

## 当前应用结构

- Kotlin 源码位于 `app/src/main/java/io/github/chenlongapps/modelatlas/`。`ModelAtlasApplication` 负责初始化 Hilt；`MainActivity` 承载 `ModelAtlasTheme` → `ModelAtlasApp`，后者通过横向分页承载 `ModelListRoute` 与设置页，底部为 Backdrop 悬浮玻璃导航。设置提供“数据来源与许可”独立页面入口，集中展示来源、价格说明与 MIT 全文；子页面隐藏主分页器与导航，支持按钮及系统返回。
- `DefaultModelRepository` 并行请求 Models.dev 两个端点，经独立 DTO 映射后通过 `CatalogDao` 原子更新 Room3 目录；`observeModels()` 合并规范模型左连接 Flow 与独立报价投影 Flow，按规范模型 ID 去重并执行 LLM 收录规则，展示模型名、LAB（稳定 ID 前缀）及其彩色 logo（无随包素材时显示兜底图）、发布时间、上下文长度、最大输出、输入／输出模态与开放权重（`open_weights` 三态，权重公开时附 license），以及一条按官方优先规则选出的报价（输入／输出、缓存、推理、音频单价与上下文档位，单位 USD / 百万 tokens，不显示服务商），不混用服务商限制。DTO、应用模型与 Entity 保持独立，存储契约见 [本地目录数据](docs/local-data.md)。网络、数据库和仓库由 `di/DataModule.kt` 配置；清单已声明普通 `INTERNET` 权限。
- `ModelListRoute` 负责调用 `hiltViewModel()`，并以生命周期感知的方式收集状态。保持 `ModelListScreen(uiState)` 不依赖注入机制，以确保浅色和深色模式预览正常工作。
- Debug 底栏为“模型｜设置｜调试”，调试是同级顶层页而非设置子页，无返回按钮、持续显示底栏并避让实测导航高度；Release 保持两栏。`src/debug/java/.../ui/debug/DebugFeature.kt` 提供导航条目与真实页面，`src/release/java/.../ui/debug/DebugFeature.kt` 的条目为 null、内容为空，专用资源仅在 `src/debug/res/`，不使用 `BuildConfig.DEBUG`。同一 `GlassNavigationItem` 列表驱动分页数量与底栏；模型双击命中区按实际栏数／间距及 RTL 计算。调试页展示／复制应用及设备信息、通过既有事务接口读取全量目录三表数量，切入停稳或手动操作后读取，离屏预组合不读库；复用仓库手动刷新，不提供清空操作，成功后通过 `ModelListViewModel.onCatalogRefreshedExternally()` 只同步本地状态、不再联网。Screen 不依赖注入，统计非实时，失败明确标记旧值，切页保留滚动与操作状态。
- 点击模型卡片打开独立全屏 `ModelDetailRoute`，隐藏主分页器与悬浮导航，返回保留查询、列表位置及报价展开。详情通过 `observeModelDetail(id)` 事务读取规范模型及所有显式关联的服务／服务商，使用独立 `ModelDetail` 应用模型，按“概览／价格／评测”三个标签切换；概览依次展示能力、技术参数、权重许可、相关资料与来源时效。页头展示居中放大的身份与描述，下接独立参数卡，直接沿用页面背景、不绘制彩色渐变，复用列表素材、标签与缩写，技术参数和读屏保留精确值。身份区与标签栏随内容滚动；返回栏固定在顶部安全区，根据大标题实测位置随上滑由品牌名渐变为模型名称，反向滚动恢复。各标签独立保存滚动与展开状态，窄屏大字号空间不足时使用可横向滚动标签。价格展示全部服务商、模型服务、基础／独立模式报价与精确档位，服务参数不使用规范模型补齐；评测读取本地已有 Benchmark 的原始分数和元数据，不推断百分比、分数方向或排名。ViewModel 只观察当前模型，退出动画结束后取消订阅，本地读取失败重试不联网；Screen 保持可独立预览。详情展示完整许可与安全 HTTP(S) 外链，不展示请求 body／headers。`ModelSharedTransition.kt` 与外壳 `SharedTransitionLayout` 按规范模型 ID 匹配整卡及 `ModelDetailContainer`，约 450ms 扩展到全屏／缩回原卡片，动态圆角与底色连续过渡，背景目录不整页淡出。同时保留 Logo／名称／机构／权重的共享身份动画，键为规范模型 ID + 元素角色，与容器键隔离；身份位于容器上层并继承其动态裁剪，不叠加父级缩放或局部内容淡化。其他内容按宽度等比缩放、局部淡化交接。主分页器始终保留组合，退出页面立即屏蔽触摸与读屏，动画结束才停止放置或移除详情。加载头部仅用不持久化的身份快照，失败／移除不保留；已滚动详情停用身份匹配但仍收缩整页，来源卡片缺失或无容器匹配时才淡出兜底，不强制滚回头部，不包含预测性返回进度联动。
- 模型查询由 `ModelListQuery` 和 `ModelListViewModel.setQuery()` 管理，完整 `models` 与筛选后的 `visibleModels` 分开，避免无匹配被误判为空目录／首次加载；条件通过 `SavedStateHandle` 恢复，不因刷新失败或切页清空。胶囊搜索框、可移除条件标签和筛选面板位于 `ModelCatalogControls.kt`；面板草稿只有点击“应用”才提交，关闭放弃本次修改，仅使用已有列表字段，不为筛选新增网络请求或混用服务商限制。
- `ModelsDevParser` 支持两个端点的字符串解析，与 Retrofit 共用 JSON 配置；`LlmInclusionPolicy` 负责收录规则。列表 ViewModel 优先读取并观察本地目录，有数据时后台检查完整目录：仅规范模型 ID 集合增删时提示，确认后应用暂存完整快照；ID 集合不变、仅内容变化时静默写入最新快照，不显示待更新或成功提醒，内容一致不写库。下拉刷新主动获取并提交（检查进行中复用请求）。失败保留本地列表，无本地数据时提供首次加载与重试，不使用运行时示例。报价已展示：每个规范模型取一条，官方第一方优先，否则按 `PriceProviderPolicy` 的第三方优先级选一家，缺失显示“暂无数据”；计价单位及适用条件见 [数据源说明](docs/data-source.md)。

## 容易遗漏的修改位置

- Compose 颜色定义在 `ui/theme/Theme.kt` 中；Activity 和窗口主题则单独定义在 `app/src/main/res/values/themes.xml` 和 `values-night/themes.xml` 中。修改主题时可能需要同时调整这两层。
- 发布版本优化使用 `app/build.gradle.kts` 中的 `optimization` DSL；项目的 R8 规则应放在 `app/src/main/keepRules/*.keep` 中，不要新建 `proguard-rules.pro`。
- LAB logo 素材在 `app/src/main/res/drawable/ic_lab_<lab>.xml`，兜底图为 `ic_lab_logo_fallback.xml`；前缀到素材的映射在 `ui/modellist/LabLogo.kt`。素材保留品牌原色、不随主题变色，因此 logo 使用固定浅色圆角底；新增素材时同步补映射与测试，不要借用其他实验室的素材。
