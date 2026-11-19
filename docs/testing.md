# 分级测试

按 [AGENTS.md](../AGENTS.md#构建与验证) 选范围。先列实际要跑的类／方法：小改只测受影响行为；改导航补入口与返回，改共享组件覆盖各使用方。纯文档只核对链接、锚点、命令和描述，不构建、不启动模拟器。

## 选测入口

用例位于 `app/src/` 的 `test/`、`testDebug/`、`androidTest/`、`androidTestDebug/`。下表为起点，不是固定回归清单。

| 改动 | 优先检查 |
| --- | --- |
| 目录、搜索和刷新 | `ModelListQueryTest`、`ModelListViewModelTest`、`ModelRepositoryTest`；设备用 `ModelCatalogControlsTest`、`SilentCatalogUpdateTest` 检查后台冻结与主动发布 |
| 详情、收藏和对比 | `ModelDetailMapperTest`、`ModelDetailScreenTest`、`ModelDetailNavigationTest`；按范围选 `FavoritesViewModelTest`、`FavoriteDatabaseTest`、`ModelCompareViewModelTest`、`ModelCompareScreenTest` |
| 评测、榜单和 Logo | `BenchmarkPresentationTest`、`BenchmarkBoardMapperTest`、`BenchmarkChartTest`、`BenchmarkLogoTest`；设备用 `ModelBenchmarkCardTest`、`BenchmarkScreenTest`、`BenchmarkRefreshTest`、`BenchmarkReentryTest` |
| 外观、底栏和导航 | `AppearanceViewModelTest`、`SettingsRepositoryTest`、`NavigationScrollBehaviorTest`；设备用 `AppearanceNavigationTest`、`NavigationScrollTest`、`GlassNavigationBarTest`、`ModelAtlasAppTest`。显隐专项需开启默认关闭的偏好 |
| 共享转场 | `ModelSharedTransitionTest`、`BenchmarkSharedTransitionTest` 及各自零动画用例；检查中间帧、暂停、取消、确认和来源移除 |
| 数据源和 Room | `AtlasReleaseLoaderTest`、`AtlasCatalogMapperTest`、`ModelsDevApiTest`、`ModelsDevParserTest`、`ModelPriceMapperTest`；事务用 `AtlasCatalogIntegrationTest`、`CatalogDatabaseTest`、`BenchmarkRankingDatabaseTest` |
| Debug 数据库 | `DebugDatabaseSchemaTest`、`DebugDatabaseStoreTest`、`DebugDatabaseScreenTest`；构建隔离另见[双版本说明](debug-release-coexistence.md#变更后的验证边界) |

保留单元、实际 Room 事务、设备 UI 各层的验证，不以 KSP 编译替代数据库测试。沿用 JUnit4、协程虚拟时间、Fake 和 Compose 语义／像素测试，不为精简引入新框架或复制测试数据库。

### AI

| 改动 | 优先检查 |
| --- | --- |
| 请求 | `OpenAiCompatibleClientTest`、`LlmLiveTestConfigTest`：协议、SSE、取消、关闭与脱敏 |
| 聊天 | `ChatViewModelTest`、`ChatScreenTest`、`ChatNavigationTest`：Fake 请求、真实 IME 两阶段返回、重建／预测性取消不重复发送、退出后旧操作不可用 |
| 配置存储 | `LlmConfigRepositoryTest`、`KeystoreConfigTest`、`AppearanceBackupRulesTest`：失败不覆盖、确认清除、真实 Keystore 与备份排除；只用测试别名和文件 |

不默认调用付费 API；[线上冒烟](llm-api.md#线上冒烟)只在显式启用后执行，默认跳过不算通过。

## 执行命令

一次选择多个单元类；选单元方法时用 `类名.方法名`：

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'io.github.chenlongapps.modelatlas.ui.modellist.ModelListQueryTest' \
  --tests 'io.github.chenlongapps.modelatlas.ui.modellist.ModelListViewModelTest'
```

设备测试先用 `adb devices -l` 确认序列号，设备须为 API 33+。同轮一次构建、安装两个 APK，再按需更换 runner 的类／方法范围：

```sh
export ANDROID_SERIAL=emulator-5554 # 替换为实际序列号
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class 'io.github.chenlongapps.modelatlas.ui.modellist.ModelCatalogControlsTest,io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailScreenTest' \
  io.github.chenlongapps.modelatlas.debug.test/androidx.test.runner.AndroidJUnitRunner
```

指定设备方法时用 `类名#方法名`。失败先定向复跑，再补受影响范围；代码、资源或测试变化后重建对应 APK。不为常规验证清数据、卸载、重建模拟器或关闭全局动画。

### 视觉诊断

`BenchmarkScreenTest`、`BenchmarkSharedTransitionTest`、`ChatNavigationTest` 支持 runner 参数 `-e captureRegression true`，截图在 Debug 应用外部专属目录 `files/regression-screenshots/`。预测性取消的像素失败证据在 `files/predictive-failures/`，日志标签为 `PredictiveCancelTest`。不要同时启动其他 UI instrumentation；截图超时与正常截图中的内容缺失分开判断。

## 核对结果

- 记录源码／APK、设备 API、实际类／方法、`numtests` 和最终通过／失败／跳过数。缺类、零项或中断不算整批通过；`-e log true` 只列举。显式 adb 不更新 Gradle 报告。
- 区分首次失败、复跑与未执行范围；连续未复现不等于找到并修复根因。测试流水不写进功能文档。
- Mock 响应、模拟器、dispatcher 或注入的触摸探索状态不能替代线上 HTTPS、真机、系统边缘手势、真实 TalkBack 或备份迁移验收。
