# 仓库说明

## 文档阅读指引

- 开始任务前先阅读 [文档索引](docs/README.md)，再按任务选择相关文档，不必一次读取全部文档。
- 修改应用结构、界面状态或数据流前，阅读 [架构说明](docs/architecture.md)；涉及 Models.dev、字段映射或计价时，阅读 [数据源说明](docs/data-source.md)。
- 必要的项目背景文档统一放在 `docs/`，使用中文并区分已实现与规划内容。构建规范保留在本文件，任务进度保留在 [TODO.md](TODO.md)；实现变化时同步更新相关文档，避免重复维护。

## 构建与验证

- 项目只有一个模块：`:app`。使用 `./gradlew`；虽然 Java 源码和目标兼容性均为 17，但 Gradle 守护进程需要 **JDK 25**（见 `gradle/gradle-daemon-jvm.properties`）。编译和目标 SDK 均为 **37**。
- 依赖和插件的版本统一定义在 `gradle/libs.versions.toml` 中。AGP 9 内置 Kotlin 支持：不要添加 `org.jetbrains.kotlin.android`。Hilt 和 Room3 的处理器使用 **KSP**，而非 kapt；Room 依赖使用 `androidx.room3`，而非 `androidx.room`。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests 'io.github.chenlongapps.modelatlas.ui.modellist.ModelListViewModelTest'
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest # 需要 API 29 及以上的设备或模拟器
```

## 当前应用结构

- Kotlin 源码位于 `app/src/main/java/io/github/chenlongapps/modelatlas/`。`ModelAtlasApplication` 负责初始化 Hilt；`MainActivity` 承载 `ModelAtlasTheme` → `ModelListRoute`。
- `ModelRepository.getModels()` 同步向 `ModelListViewModel.uiState` 提供三个本地示例。Retrofit/OkHttp、序列化库和 Room3 目前仅作为依赖引入：尚未实现 API 客户端或数据库，清单文件中也未声明 `INTERNET` 权限。
- `ModelListRoute` 负责调用 `hiltViewModel()`，并以生命周期感知的方式收集状态。保持 `ModelListScreen(uiState)` 不依赖注入机制，以确保浅色和深色模式预览正常工作。
- 计划接入 Models.dev。`README.md` 和 `TODO.md` 中列出的端点不同，且均无对应实现。实现请求前，请核实端点、数据结构和计价单位。

## 容易遗漏的修改位置

- Compose 颜色定义在 `ui/theme/Theme.kt` 中；Activity 和窗口主题则单独定义在 `app/src/main/res/values/themes.xml` 和 `values-night/themes.xml` 中。修改主题时可能需要同时调整这两层。
- 发布版本优化使用 `app/build.gradle.kts` 中的 `optimization` DSL；项目的 R8 规则应放在 `app/src/main/keepRules/*.keep` 中，不要新建 `proguard-rules.pro`。
