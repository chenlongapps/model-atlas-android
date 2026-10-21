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
- `ModelsDevParser` 支持两个端点的字符串解析，与 Retrofit 共用 JSON 配置；`LlmInclusionPolicy` 负责收录规则。列表 ViewModel 优先读取并观察本地目录，有数据时后台检查完整目录变化，仅提示可更新；确认后应用暂存快照，下拉刷新则主动获取并提交（检查进行中复用请求）。失败保留本地列表，无本地数据时提供首次加载与重试，不使用运行时示例。报价已展示：每个规范模型取一条，官方第一方优先，否则按 `PriceProviderPolicy` 的第三方优先级选一家，缺失显示“暂无数据”；计价单位及适用条件见 [数据源说明](docs/data-source.md)。

## 容易遗漏的修改位置

- Compose 颜色定义在 `ui/theme/Theme.kt` 中；Activity 和窗口主题则单独定义在 `app/src/main/res/values/themes.xml` 和 `values-night/themes.xml` 中。修改主题时可能需要同时调整这两层。
- 发布版本优化使用 `app/build.gradle.kts` 中的 `optimization` DSL；项目的 R8 规则应放在 `app/src/main/keepRules/*.keep` 中，不要新建 `proguard-rules.pro`。
- LAB logo 素材在 `app/src/main/res/drawable/ic_lab_<lab>.xml`，兜底图为 `ic_lab_logo_fallback.xml`；前缀到素材的映射在 `ui/modellist/LabLogo.kt`。素材保留品牌原色、不随主题变色，因此 logo 使用固定浅色圆角底；新增素材时同步补映射与测试，不要借用其他实验室的素材。
