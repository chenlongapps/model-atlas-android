# 仓库说明

## 文档阅读指引

- 开始任务前先阅读 [文档索引](docs/README.md)，再按任务选择相关文档，不必一次读取全部文档。
- 修改应用结构、界面状态或数据流前，阅读 [架构说明](docs/architecture.md)；涉及 Models.dev、字段映射或计价时，阅读 [数据源说明](docs/data-source.md)。
- 必要的项目背景文档统一放在 `docs/`，使用中文并区分已实现与规划内容。构建规范保留在本文件，任务进度保留在 [TODO.md](TODO.md)；实现变化时同步更新相关文档，避免重复维护。

## 构建与验证

- 项目只有一个模块：`:app`。使用 `./gradlew`；虽然 Java 源码和目标兼容性均为 17，但 Gradle 守护进程需要 **JDK 25**（见 `gradle/gradle-daemon-jvm.properties`）。编译和目标 SDK 均为 **37**。
- 依赖和插件的版本统一定义在 `gradle/libs.versions.toml` 中。AGP 9 内置 Kotlin 支持：不要添加 `org.jetbrains.kotlin.android`。Hilt 和 Room3 的处理器使用 **KSP**，而非 kapt；Room 依赖使用 `androidx.room3`，而非 `androidx.room`。Room3 列转换使用 `@ColumnTypeConverter` / `@ColumnTypeConverters`，而非 Room2 的注解。
- 默认按改动选测，不把完整模拟器回归作为每次修改的固定流程：纯文档只检查文档；逻辑改动跑相关单元测试；UI 改动再跑对应页面／方法的 instrumentation 测试。功能完成时覆盖相关测试类和受影响的导航／返回链路，按风险补充 Debug 构建、Lint 与视觉检查。
- 发布前执行全量单元测试、Debug Lint 和全量 instrumentation 回归，并补齐必要真机验收；跨功能共享组件、全局主题、导航／转场、数据存储或依赖／构建配置的大范围修改也应扩大回归。普通页面改动不默认追加 Release 全套；涉及 Release 优化、资源／构建类型隔离、签名或发布时再验证 Release 构建与 Lint。
- 同一轮选定的 instrumentation 用例合并构建、安装后批量执行；源码／测试未变时复用本轮 APK，失败先定向重跑，修复后重跑受影响范围，不无条件重跑全套。不要为常规验证执行 `clean`、清数据、卸载或重建模拟器。选测参考、命令与结果记录见 [分级测试与定向回归](docs/testing.md)。以下为可用命令，不是每次必须全部执行的清单。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests 'io.github.chenlongapps.modelatlas.ui.modellist.ModelListViewModelTest'
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest # 全量；设备／模拟器需满足当前 minSdk（33）
```

指定或切换 instrumentation 测试类时，核对报告中的实际类名与数量，不要仅凭 Gradle 成功状态判定范围。本机曾出现 `android.testInstrumentationRunnerArguments.class` 未按命令中全部类执行的情况，关闭配置缓存也未解决；可安装构建出的应用与测试 APK，再用 `adb shell am instrument -w -r -e class <测试类列表> io.github.chenlongapps.modelatlas.debug.test/androidx.test.runner.AndroidJUnitRunner` 显式验证，并检查 `numtests`、类名及最终 `OK` 结果。

## 当前应用结构

- Kotlin 源码位于 `app/src/main/java/io/github/chenlongapps/modelatlas/`。`ModelAtlasApplication` 负责初始化 Hilt；`MainActivity` 承载 `ModelAtlasTheme` → `ModelAtlasApp`，后者通过横向分页承载模型、对比与设置页，底部为 Backdrop 悬浮玻璃导航。设置提供“外观”及“数据来源与许可”独立页面入口，后者集中展示来源、价格说明与 MIT 全文；子页面隐藏主分页器与导航，支持按钮及系统返回。
- `DefaultModelRepository` 并行请求 Models.dev 两个端点，经独立 DTO 映射后通过 `CatalogDao` 原子更新 Room3 目录；`observeModels()` 合并规范模型左连接 Flow 与独立报价投影 Flow，按规范模型 ID 去重并执行 LLM 收录规则，展示模型名、LAB（稳定 ID 前缀）及其彩色 logo（无随包素材时显示兜底图）、发布时间、上下文长度、最大输出、输入／输出模态与开放权重（`open_weights` 三态，权重公开时附 license），以及一条按官方优先规则选出的报价（输入／输出、缓存、推理、音频单价与上下文档位，单位 USD / 百万 tokens，不显示服务商），不混用服务商限制。DTO、应用模型与 Entity 保持独立，存储契约见 [本地目录数据](docs/local-data.md)。网络、数据库和仓库由 `di/DataModule.kt` 配置；清单已声明普通 `INTERNET` 权限。
- `data/repository/SettingsRepository.kt` 通过单例 Preferences DataStore 持久化无 Compose 依赖的 `AppearanceSettings`，暴露 Flow 与原子字段修改接口；本次没有 AI 调用入口。Activity 级 `AppearanceViewModel` 管理全局实时预览、300ms 防抖／松手提交和失败回滚，首读最多等待 1.5 秒后放行首帧，读取失败默认主题并禁写、提供重试，不自动覆盖原文件。设置新增独立外观子页，明暗（浅／深／系统）与配色（系统动态色＋14 预设＋单色相滑块）独立，完整色板由 MaterialKolor 生成；不要仅替换 `primary`、使用受限 Views utilities 或把“跟随系统明暗”当作动态色开关。设置文件 `files/datastore/appearance.preferences_pb` 在两套备份规则中单独排除；其他数据规则不变。详见 [外观设置与持久化](docs/appearance-settings.md)。
- `ModelListRoute` 负责调用 `hiltViewModel()`，并以生命周期感知的方式收集状态。保持 `ModelListScreen(uiState)` 不依赖注入机制，以确保浅色和深色模式预览正常工作。
- Debug 底栏为“模型｜对比｜设置｜调试”，调试是同级顶层页而非设置子页，无返回按钮、持续显示底栏并避让实测导航高度；Release 为“模型｜对比｜设置”三栏。`src/debug/java/.../ui/debug/DebugFeature.kt` 提供导航条目与真实页面，`src/release/java/.../ui/debug/DebugFeature.kt` 的条目为 null、内容为空，专用资源仅在 `src/debug/res/`，不使用 `BuildConfig.DEBUG`。同一 `GlassNavigationItem` 列表驱动分页数量与底栏，按条目标识分发页面；模型双击命中区按实际栏数／间距及 RTL 计算。调试页展示／复制应用及设备信息、通过既有事务接口读取全量目录三表数量（不包含独立收藏表），切入停稳或手动操作后读取，离屏预组合不读库；复用仓库手动刷新，成功后通过 `ModelListViewModel.onCatalogRefreshedExternally()` 只同步本地状态、不再联网。另有独立数据库管理子页，逐条浏览和增删改查 `catalog.db` 四张表；不提供批量清空／任意 SQL，目录同步可能覆盖手动修改，服务商删除会级联，收藏删除不可撤销。Debug 工具通过同一 Room 连接、字段白名单和事务操作，变更后只恢复本地观察并保留待更新提示；详情见 [本地目录数据](docs/local-data.md)。Screen 不依赖注入，原统计非实时，失败明确标记旧值，切页保留滚动与操作状态。
- `ui/favorites/` 提供本地收藏观察与详情收藏状态，模型目录将收藏稳定前置；可用收藏组内沿用发布时间顺序，下架收藏保留历史身份并可进入状态页取消，不显示旧参数／价格。`favorites` 是独立用户表，不设目录外键、不参与远端快照替换；目录提交同事务更新仍可用的身份。仅详情右上角提供操作，收藏后停留，取消保存成功后返回；失败不返回。详情打开期间固定来源卡位置，返回转场完成后才重排；下架收藏删除时暂留来源并淡出，不延后持久化。Schema 不变。
- `ui/modelcompare/` 提供独立双模型对比页。选择面板复用本地名称／ID／LAB 搜索，禁止重复选择，支持替换／移除；`ModelCompareViewModel` 只保存两个规范模型 ID，在页面切入停稳且生命周期可见时观察目录和所选详情，离屏取消，不联网。参数／能力取规范模型，价格直接沿用 `ModelSummary.price` 默认报价，显示服务商与服务 ID、七类单价和完整精确档位，不混合服务／独立模式或推断能力排名。Screen 不依赖注入，分组对照按宽度／字号使用字段＋双列、分项双列或上下布局，读屏保留精确值；正文位置及档位展开在 UI 保存，加载／失败／移除不夹回正文位置，错误本地重试，移除不保留旧对比内容。
- 点击模型卡片打开独立全屏 `ModelDetailRoute`，隐藏主分页器与悬浮导航，返回保留查询、列表位置及报价展开。详情通过 `observeModelDetail(id)` 事务读取规范模型及全部显式关联服务，应用模型和存储保留完整数据；界面按“概览／价格／评测”切换。公共头部仅保留居中身份、描述、模态及明确支持的能力标签，不再重复参数卡。概览保留技术参数（含参数量缺失提示，不含系列）、模态（输入／输出文字，缺失与空集合分别为“暂无数据”与“无”）及权重许可（不显示权重链接或格式／量化），不显示能力明细、相关资料或来源时效。参数量当前上游未提供，不从名称猜测、不新增来源或数据库列。价格每个服务独立一条、含无报价服务，仅显示服务商、模型服务 ID、上下文长度、最大输出、价格（基础输入／输出价），仅取对应服务，缺失不从规范模型、其他服务、模式或档位补齐；列表与对比报价不变。评测保留本地原始分数和元数据，不推断百分比、方向或排名。各标签独立保存阅读位置，窄屏大字号标签可横滚；固定返回栏按实测大标题位置由品牌名交接为模型名。ViewModel 只观察当前模型，退出动画结束后取消，本地重试不联网；Screen 可独立预览，不展示请求 body／headers。`ModelSharedTransition.kt` 与外壳按规范模型 ID 匹配整卡及 `ModelDetailContainer`，约 450ms 扩展／缩回，动态圆角与底色连续，背景目录不整页淡出；Logo／名称／机构／权重以 ID + 角色隔离共享键，身份在容器上层继承动态裁剪，不叠加缩放或局部淡化。其他内容等比缩放并淡化交接。主分页器持续组合，退出页立即禁用触摸与读屏，动画结束才隐藏或清理。加载只用不持久化身份快照，失败／移除不保留；滚动后停用身份匹配但仍缩回整页，来源缺失时才淡出，不强制回顶。预测性返回由 `ModelDetailTransitionController` 寻址两套保留可见性 Transition，暂停保留布局，取消沿时间轴恢复，确认完成剩余收缩，端点交接后才清理，兼容细节见架构文档。
- 模型查询由 `ModelListQuery` 和 `ModelListViewModel.setQuery()` 管理，完整 `models` 与 `visibleModels` 分开，避免无匹配误判空目录／首次加载。筛选仅 LAB、权重公开状态、输入／输出模态；最低上下文、最低最大输出与有无报价已连同匹配／计数／标签移除。查询和草稿共用 `ModelListQueryJson`／`restoreModelListQuery()`，恢复旧 JSON 时忽略删除字段但保留有效条件，损坏值兜底。刷新失败和切页不清空。`ModelCatalogControls.kt` 提供搜索、可移除标签和面板，草稿仅“应用”提交、关闭放弃，不新增请求或混用服务限制。
- `ModelsDevParser` 支持两个端点的字符串解析，与 Retrofit 共用 JSON 配置；`LlmInclusionPolicy` 负责收录规则。列表 ViewModel 优先读取并观察本地目录，有数据时后台检查完整目录：仅规范模型 ID 集合增删时提示，确认后应用暂存完整快照；ID 集合不变、仅内容变化时静默写入最新快照，不显示待更新或成功提醒，内容一致不写库。下拉刷新主动获取并提交（检查进行中复用请求）。失败保留本地列表，无本地数据时提供首次加载与重试，不使用运行时示例。报价已展示：每个规范模型取一条，官方第一方优先，否则按 `PriceProviderPolicy` 的第三方优先级选一家，缺失显示“暂无数据”；计价单位及适用条件见 [数据源说明](docs/data-source.md)。

## 容易遗漏的修改位置

- 详情概览的专用排版在 `ui/modeldetail/ModelOverview.kt`：保留三张轻量圆角卡片，不嵌套指标小卡或彩色圆形图标；技术参数突出上下文／最大输出（精确值与 tokens 分层），最大输入／参数量为次级信息，日期在分隔线后的双列页脚，实测宽度不足／大字号逐组降为单列；模态固定为“输入／输出”上下两行，起始侧标签对齐、末侧图标＋文字换行，无箭头，仅列来源已有项；权重复用非交互状态标签、完整许可为下方摘要。只改概览时不要影响公共头部、服务卡片和评测共用字段，配色使用主题语义色。
- Compose 颜色定义在 `ui/theme/Theme.kt` 中；Activity 和窗口主题则单独定义在 `app/src/main/res/values/themes.xml` 和 `values-night/themes.xml` 中。修改主题时可能需要同时调整这两层。
- 发布版本优化使用 `app/build.gradle.kts` 中的 `optimization` DSL；项目的 R8 规则应放在 `app/src/main/keepRules/*.keep` 中，不要新建 `proguard-rules.pro`。
- LAB logo 素材在 `app/src/main/res/drawable/ic_lab_<lab>.xml`，兜底图为 `ic_lab_logo_fallback.xml`；前缀到素材的映射在 `ui/modellist/LabLogo.kt`。素材保留品牌原色、不随主题变色，因此 logo 使用固定浅色圆角底；新增素材时同步补映射与测试，不要借用其他实验室的素材。
- 详情价格轻量卡片在 `ui/modeldetail/ModelServiceCard.kt`：顶部 logo／服务商／完整服务 ID，底部上下文／最大输出／基础输入／输出价四列，按实测标签与精确原值宽度降为两列／单列，价格单位在列表顶部统一显示。服务商 logo 独立放在 `app/src/main/res/drawable/ic_provider_*.xml`，映射与组件在 `ui/modeldetail/ProviderLogo.kt`，仅按服务商稳定 ID 匹配、不借用模型 LAB。素材来自 `https://models.dev/logos/{provider-ID}.svg` 并随包打包；来源通用图、无法完整转换或未知 ID 使用 `ic_provider_logo_fallback.xml`。新增素材同步补映射、测试与来源说明，保持白底、原色及名称完整换行。
