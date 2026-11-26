# 分级测试

## 从 Diff 选测试

每轮修改完成、验证之前，先用 `git status --short --untracked-files=all` 查看状态，再用 `git diff HEAD --` 审查暂存和未暂存改动，并单独检查相关未跟踪文件。如果改动已提交，将比较基准换为本轮起点，不能把空工作区 Diff 当成没有影响。

按变更的行为和调用方列出要跑的类／方法，不仅按目录匹配。例如修改 `ui/` 下的纯格式化逻辑仍优先选 JVM；修改共享组件则检查受影响的使用方，而不是直接跑全量。

| Diff 的实际影响 | 选择 |
| --- | --- |
| 仅文档 | 检查链接、锚点、命令和描述，不构建、不启动模拟器 |
| 业务逻辑、格式化、解析、ViewModel、JVM 测试 | 只跑受影响的 JVM 类／方法；不为此启动模拟器 |
| Compose 渲染、交互或导航 | 相关 JVM 测试，加受影响的页面／交互 Instrumentation；导航覆盖入口和返回链路，按需做视觉检查 |
| Room Schema、DAO、SQL、事务，或 IME、Keystore、备份等系统行为 | 相关 JVM 测试，加对应的设备集成测试；纯数据映射不默认跑 Room |
| 共享组件、全局主题、依赖或构建 | 跨使用方扩展定向覆盖；仅当风险需要时补 Debug 构建／Lint。Release 构建／Lint 只在涉及 Release 优化、资源／构建类型隔离、签名或发布时追加 |

完整回归仅在重大架构调整、发布前或明确要求时执行，覆盖全量 JVM、Debug Lint、全量 Instrumentation 和必要的真机验收。必要测试不能因耗时删除：优先把可纯 JVM 验证的规则留在单元层，把真实 SQLite、渲染、导航和系统契约留在设备层。没有合适用例时补覆盖或说明缺口，不用全量执行代替判断。

## 选测入口

用例位于 `app/src/` 的 `test/`、`testDebug/`、`androidTest/`、`androidTestDebug/`。下表为起点，不是固定回归清单。

| 改动 | 优先检查 |
| --- | --- |
| 目录、搜索和刷新 | `ModelListQueryTest`、`ModelListViewModelTest`、`ModelRepositoryTest`；设备用 `ModelCatalogControlsTest`、`SilentCatalogUpdateTest` 检查后台冻结与主动发布 |
| 详情、收藏和对比 | `ModelDetailMapperTest`、`ModelDetailScreenTest`、`ModelDetailNavigationTest`；按范围选 `FavoritesViewModelTest`、`FavoriteDatabaseTest`、`ModelCompareViewModelTest`、`CompareBenchmarkGroupsTest`、`ModelCompareScreenTest` |
| 评测、榜单和 Logo | `BenchmarkPresentationTest`、`BenchmarkBoardMapperTest`、`BenchmarkChartTest`、`BenchmarkLogoTest`；设备用 `ModelBenchmarkCardTest`、`BenchmarkScreenTest`、`BenchmarkRefreshTest`、`BenchmarkReentryTest` |
| 外观、底栏和导航 | `AppearanceViewModelTest`、`SettingsRepositoryTest`、`NavigationGeometryTest`、`NavigationScrollBehaviorTest`；设备用 `AppearanceNavigationTest`、`NavigationScrollTest`、`GlassSurfaceTest`、`GlassNavigationBarTest`、`ModelAtlasAppTest`。底栏检查拖动中间帧、Pager 同步、松手提交、实时取样与主题着色；显隐专项需开启默认关闭的偏好 |
| 共享转场 | `ModelSharedTransitionTest`、`BenchmarkSharedTransitionTest` 及各自零动画用例；检查中间帧、暂停、取消、确认和来源移除 |
| 数据源和 Room | `AtlasReleaseLoaderTest`、`AtlasCatalogMapperTest`、`ModelsDevApiTest`、`ModelsDevParserTest`、`ModelPriceMapperTest`；事务用 `AtlasCatalogIntegrationTest`、`CatalogDatabaseTest`、`BenchmarkRankingDatabaseTest` |
| Debug 数据库 | `DebugDatabaseSchemaTest`、`DebugDatabaseStoreTest`、`DebugDatabaseScreenTest`；构建隔离另见[双版本说明](debug-release-coexistence.md#变更后的验证边界) |

当前用 JUnit4、协程虚拟时间、Fake 和 MockWebServer 做 JVM 验证；设备层用 AndroidJUnitRunner 跑 Compose 语义／像素、实际 Room 事务和系统行为测试。保留这些层的覆盖，不以 KSP 编译替代数据库测试，不为精简引入新框架或复制测试数据库。

### AI

| 改动 | 优先检查 |
| --- | --- |
| 请求 | `OpenAiCompatibleClientTest`、`LlmLiveTestConfigTest`：协议、SSE、取消、关闭与脱敏 |
| 聊天 | `ChatViewModelTest`、`ChatScreenTest`、`ChatNavigationTest`：Fake 请求、真实 IME 两阶段返回、重建／预测性取消不重复发送、退出后旧操作不可用；`AssistantRevealTest`、`AssistantRevealNavigationTest`、`AssistantRevealReducedMotionTest`：圆心／半径、双向中间帧、来源保留和零动画 |
| 回复排版 | `AssistantMarkdownTest`、`AssistantMarkdownScreenTest`，另选 `ChatScreenTest` 的流式、停止／重试、复制及阅读锚点用例 |
| 配置存储 | `LlmConfigRepositoryTest`、`KeystoreConfigTest`、`AppearanceBackupRulesTest`：失败不覆盖、确认清除、真实 Keystore 与备份排除；只用测试别名和文件 |

不默认调用付费 API；[线上冒烟](llm-api.md#线上冒烟)只在显式启用后执行，默认跳过不算通过。

## 执行与复用

保留默认 Gradle Daemon（JDK 25）和仓库已启用的配置缓存；以下命令用 `--build-cache` 复用可缓存的任务输出。多个目标尽量在同一次 `./gradlew` 调用中执行，不默认用 `--rerun-tasks`、`--no-daemon`，也不执行 `clean`、清数据、卸载应用、重建模拟器或关闭全局动画。

### JVM：默认入口

一次传入所有受影响的单元类；需要缩到方法时，用 `全限定类名.方法名`：

```sh
./gradlew --build-cache :app:testDebugUnitTest \
  --tests 'io.github.chenlongapps.modelatlas.ui.modellist.ModelListQueryTest' \
  --tests 'io.github.chenlongapps.modelatlas.ui.modellist.ModelListViewModelTest'
```

### Instrumentation：按需批跑

1. 先用 `adb devices -l` 查找已就绪设备，选择实际需要的目标（API 33+）。优先复用已连接真机或已启动的模拟器；没有合适设备且确需测试时才启动一个既有 AVD，等待就绪后复用。多类测试不重复启动；不同设备／API 有覆盖要求时，各目标复用同一对 APK。

   ```sh
   export ANDROID_SERIAL=emulator-5554 # 替换为实际序列号
   ```

2. 已有 APK 能确认对应当前应用、测试及其构建输入时，跳过构建。否则在一次 Gradle 调用中请求两个 APK，由 Gradle 判定哪些任务需要重做；若同时需要 JVM 测试，也可将带筛选器的任务加入同一次调用。

   ```sh
   ./gradlew --build-cache :app:assembleDebug :app:assembleDebugAndroidTest
   ```

3. 在目标设备上只安装尚未安装或已变更的 APK。下面是两份都需安装的示例；只有确认当前 APK 已安装（或安装成功）才执行 runner。

   ```sh
   adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk &&
     adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
   ```

4. 将同一设备、同一 runner 配置的类／方法放进一条逗号分隔的 `-e class` 参数；方法用 `全限定类名#方法名`。完整回归时才移除 `-e class` 过滤，并另跑未加 `--tests` 的 JVM 任务及 `:app:lintDebug`。

   ```sh
   adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
     -e class 'io.github.chenlongapps.modelatlas.ui.modellist.ModelCatalogControlsTest,io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailScreenTest' \
     io.github.chenlongapps.modelatlas.debug.test/androidx.test.runner.AndroidJUnitRunner
   ```

失败先定向复跑：输入没变时复用当前 APK 和安装，只换筛选器；修复后重新看 Diff，按新影响更新产物并补测，不重复执行无关已通过用例。

### 视觉诊断

`BenchmarkScreenTest`、`BenchmarkSharedTransitionTest`、`ModelCompareScreenTest`、`ChatNavigationTest` 支持 runner 参数 `-e captureRegression true`，截图在 Debug 应用外部专属目录 `files/regression-screenshots/`。预测性取消的像素失败证据在 `files/predictive-failures/`，日志标签为 `PredictiveCancelTest`。不要同时启动其他 UI instrumentation；截图超时与正常截图中的内容缺失分开判断。

## 核对结果

- JVM 核对 `app/build/test-results/testDebugUnitTest/` 的 XML，确认实际类／方法与计数，区分本轮执行和 Gradle 标记的 `UP-TO-DATE`／`FROM-CACHE`；不要引用旧报告冒充本轮结果。
- 设备测试记录 APK、设备 API、实际类／方法、`numtests` 和最终 runner 汇总（成功时 `OK`）。本机曾出现筛选器漏跑；缺类、零项或中断不算整批通过，`-e log true` 只列举。显式 adb 不更新 Gradle 报告。
- 交付简报按 JVM／设备列出已选范围、通过／失败（含错误）／跳过数，以及未验证项和原因。区分首次失败、复跑与未执行；复跑未复现不等于根因已修复，没跑就写「未运行」。测试流水不写进功能文档。
- Mock 响应、模拟器、dispatcher 或注入的触摸探索状态不能替代线上 HTTPS、真机、系统边缘手势、真实 TalkBack 或备份迁移验收。
