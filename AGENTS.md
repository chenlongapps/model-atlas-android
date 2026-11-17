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
- **AI 安全**：配置独立 DataStore／Keystore 加密，读取／保存失败不自动覆盖；凭据不进日志、SavedState 或备份，应用运行时不读 `local.properties`。对话／草稿仅内存，不自动重发付费请求，不重定向或重放 POST；修改前读[请求契约](docs/llm-api.md)、[配置存储](docs/ai-configuration.md)及[聊天状态](docs/chat.md)。

## UI 修改

- 修改 Compose、布局、样式、交互或动画前，必须阅读 [设计规范](docs/Design.md)的原则、一致性约束及对应页面章节；展示字段另查[页面字段](docs/page-information.md)。
- 先检查目标实现、全局主题及相近组件。以 Material 3 和现有设计体系为共同约束，优先复用 `ModelAtlasTheme` 与已有组件，不照搬通用示例另造风格。
- 未经明确要求，不改全局视觉或无关页面；公共组件变更须评估所有使用位置。
- 完成后按设计规范执行验证，如实报告实际检查与未验证项；不将未执行的检查写成通过。

## 构建与验证

按影响选测，不默认全量回归。具体用例与执行方式见 [分级测试](docs/testing.md)。

| 改动 | 验证范围 |
| --- | --- |
| 纯文档 | 链接、锚点、命令及描述一致性；不构建、不启动模拟器 |
| 逻辑 | 相关单元测试；按风险补 Debug 构建与 Lint |
| UI | 相关单元及页面／方法 instrumentation；覆盖受影响导航／返回链路，按需视觉检查 |
| 跨功能共享组件、全局主题／导航、存储、依赖／构建 | 扩大受影响范围回归 |
| 发布前 | 全量单元、Debug Lint、全量 instrumentation 及必要真机验收 |

Release 构建／Lint 仅在涉及 Release 优化、资源／构建类型隔离、签名或发布时追加，不作为普通页面修改的固定流程。

常用命令（按需选择，不是每次全跑）：

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --tests 'io.github.chenlongapps.modelatlas.ui.modellist.ModelListViewModelTest'
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest # 全量，设备 API 33+
```

- 同轮 instrumentation 合并构建、安装后批量执行；源码／测试未变复用本轮 APK，失败先定向重跑，修复后补受影响范围。
- 不为常规验证执行 `clean`、清数据、卸载或重建模拟器；不默认调用真实付费 API。
- **核对实际类名、数量与最终结果**，不只看 Gradle 成功。本机曾出现 class 参数漏跑；必要时按测试文档显式运行 `adb shell am instrument`，核对 `numtests` 和最终 `OK`，缺类／零项／中断不算通过。
- 交付说明改动范围、实际验证结果及未验证风险；不冒用历史结果。
