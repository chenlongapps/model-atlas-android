# 仓库协作指南

本文件只保留常用执行规则；功能细节在 `docs/` 按主题维护，不在此重复。

## 开始任务

- 先读 [文档索引](docs/README.md)，再按任务阅读专题，不必通读全部文档。
- 修改结构、状态、数据流或导航前，读 [架构说明](docs/architecture.md)；修改数据接入、字段映射或计价前，读 [Atlas 契约](docs/atlas-data.md)及[数据源说明](docs/data-source.md)。
- Kotlin 源码根目录：`app/src/main/java/io/github/chenlongapps/modelatlas/`。入口为 `MainActivity` → `ModelAtlasTheme` → `ModelAtlasApp`；目录与职责见架构文档。
- 文档使用中文，区分当前实现、规划与实际验证。每项规则只在一个主文档维护；实现变化时同步对应专题，未闭环进度放在 [TODO.md](TODO.md)。

## 工程约束

- 单模块 `:app`，使用 `./gradlew`。Gradle 守护进程需 **JDK 25**，Java 源码／目标兼容性为 **17**；compileSdk／targetSdk 为 **37**，minSdk 为 **33**。
- 插件与依赖版本统一放在 `gradle/libs.versions.toml`。AGP 9 内置 Kotlin，**不要添加 `org.jetbrains.kotlin.android`**。
- Hilt、Room3 使用 **KSP**，不用 kapt。Room 使用 `androidx.room3` 及 `@ColumnTypeConverter`／`@ColumnTypeConverters`，不用 Room2 包名和注解；不另建重复测试数据库或 Room `kspTest`。
- Release 优化使用 `app/build.gradle.kts` 的 `optimization` DSL；R8 规则放在 `app/src/main/keepRules/*.keep`，不新建 `proguard-rules.pro`。
- Debug 功能通过 `src/debug/`／`src/release/` 同名入口隔离，专用资源仅放 `src/debug/res/`，不以 `BuildConfig.DEBUG` 代替打包隔离。
- 主题涉及两层：Compose 的 `ui/theme/Theme.kt`，以及窗口的 `app/src/main/res/values/themes.xml`、`values-night/themes.xml`；按影响同步检查。

## 开发边界

- **分层**：DTO、Entity／本地 JSON、应用模型独立。Route 获取 ViewModel 并感知生命周期收集；`Screen(uiState, callbacks)` 不依赖注入，保持可预览、可独立测试。
- **来源**：规范模型／评测／中文来自 Atlas，服务商／价格来自 Models.dev；仅按显式 `canonical_model_id` 关联。规范模型和服务字段不互补，不猜关联、不推算缺失值，区分 null、false、零与空集合。
- **快照**：目录／成绩／定义／发布状态同事务提交，收藏不参与远端替换。列表展示快照与后台缓存分离，后台写库不得自动改变当前列表；详情见[数据与状态流转](docs/architecture.md#数据与状态流转)。
- **存储**：沿用未发布的 Schema v1 开发基线，不自动迁移、破坏性重建或清数据。修改列时同步导出 Schema、Debug 字段白名单及校验，见[本地数据](docs/local-data.md)。
- **导航**：助手是 Navigation 3 独立路由，不属于主 Pager；既有详情／榜单／设置子页仍在 Main 外壳。局部修改不得附带迁移导航或改写共享转场。
- **AI 安全**：配置独立 DataStore／Keystore 加密，读取／保存失败不自动覆盖；凭据不进日志、SavedState 或备份，应用运行时不读 `local.properties`。对话／草稿仅内存，不自动重发付费请求，不重定向或重放 POST；修改前读[请求契约](docs/llm-api.md)和[聊天与配置存储](docs/chat.md)。

## UI 修改

- 修改 Compose、布局、样式、交互或动画前，必须阅读 [设计规范](docs/Design.md)的原则、一致性约束及对应页面章节；展示字段另查[页面字段](docs/page-information.md)。
- 先检查目标实现、全局主题及相近组件。以 Material 3 和现有设计体系为共同约束，优先复用 `ModelAtlasTheme` 与已有组件，不照搬通用示例另造风格。
- 未经明确要求，不改全局视觉或无关页面；公共组件变更须评估所有使用位置。
- 完成后按设计规范执行验证，如实报告实际检查与未验证项；不将未执行的检查写成通过。

## 构建与验证

每轮修改后，**先分析 Git Diff，再选测**：包含暂存、未暂存及相关新增文件，按实际受影响行为和调用方确定用例。具体选择和命令见 [分级测试](docs/testing.md)。

- 纯文档只做静态检查。代码改动默认只跑受影响的 JVM 单元测试，不启动模拟器；仅在涉及 UI、导航、数据库或系统行为时补对应的 Instrumentation。
- 同轮设备用例合并执行；优先复用已就绪设备、匹配当前改动的 APK 和已有安装。保留 Gradle Daemon 与缓存，不为日常验证执行 `clean`、清数据、卸载或重建模拟器，也不默认调用付费 API。
- 仅重大架构调整、发布前或明确要求时做完整回归。需要扩大覆盖时先补受影响范围，不因耗时删除必要测试。
- 核对实际执行的类／方法和结果；交付简报测试范围、通过／失败／跳过数及未验证项。
