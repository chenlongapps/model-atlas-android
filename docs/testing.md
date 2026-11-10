# 分级测试与定向回归

构建环境、分级要求与发布命令以 [AGENTS.md](../AGENTS.md#构建与验证) 为准；本文维护选测入口、执行方式及结果判定，不记录历史通过总数。执行状态见 [TODO](../TODO.md#待验收与发布)，以往记录见[历史](archive/development-history.md)。

## 先选范围

先列影响与拟执行类／方法，小改可选方法，功能完成补相关类及导航／返回链路；共享组件、全局主题／导航、存储、依赖或构建的大范围修改扩大回归。以下是起点，不是每次全跑的套件；测试源码为 `app/src/test/`、`src/testDebug/`、`src/androidTest/`。

| 改动 | 优先测试 | 按影响追加 |
| --- | --- | --- |
| 纯文档 | 相对链接、标题锚点、命令与当前描述 | 不构建、不启动模拟器 |
| 搜索／筛选 | `ModelListQueryTest`、`ModelListViewModelTest`、`ModelCatalogControlsTest` | 卡片／状态加 `ModelListScreenTest`，切页恢复加 `ModelAtlasAppTest` |
| 目录刷新／反馈／骨架 | `ModelRepositoryTest`、`CatalogSnapshotTest`、列表 ViewModel、`CatalogEntriesTest`、`CatalogStatusNoticeTest` | `SilentCatalogUpdateTest`（后台冻结／主动发布／切页及详情返回）、对应 Screen、`CatalogNoticeAnimationTest`／`CatalogNoticeReducedMotionTest`、`ModelCardSkeletonTest`／`ModelCardSkeletonReducedMotionTest` |
| 详情字段／布局 | `ModelDetailMapperTest`、`ModelDetailViewModelTest`、`ModelDetailScreenTest` | `ModelDetailNavigationTest`，身份／容器变化再加共享转场 |
| 详情评测／Logo | `BenchmarkPresentationTest`、`BenchmarkLogoTest`、详情／Atlas Mapper、`ModelBenchmarkCardTest` | 详情排序／标签／滚动及返回；全矢量加载、浅深原色白底、文本／明示单位、窄屏大字 RTL |
| 对比 | `ModelCompareQueryTest`、`ModelCompareViewModelTest`、`ModelCompareScreenTest` | 报价变化加 `ModelPriceMapperTest`／`PriceProviderPolicyTest` 及列表；覆盖等高、替换重置、异步恢复 |
| 收藏／收藏标识 | `FavoritesViewModelTest`、`FavoriteRemovalTransitionTest`、`CatalogEntriesTest`、`CatalogFavoritesTest`、`FavoritesScreenTest`、`ModelCardFavoriteLayoutTest` | 从模型目录进入详情，不使用独立收藏导航；存储加 `FavoriteDatabaseTest`，返回加共享转场／零动画 |
| 外观／主题 | `SettingsRepositoryTest`、`AppearanceViewModelTest`、`AppearanceColorTest`、`AppearanceScreenTest`、`AppearanceNavigationTest` | 全局变化加 `ModelAtlasThemeTest` 与主要页面浅深；备份变化加 `AppearanceBackupRulesTest` |
| Models.dev／价格 | `ModelsDevApiTest`、`ModelsDevParserTest`、`InterleavedDtoSerializerTest`、相关 Mapper／Policy／Repository | Room／事务变化加 `CatalogDatabaseTest`，共享字段覆盖所有消费者 |
| Atlas 发布包 | `AtlasReleaseLoaderTest`、`AtlasHttpSourceTest`、`AtlasCatalogMapperTest`、`ModelRepositoryTest` | `AtlasCatalogIntegrationTest`，共享存储加 Catalog／Favorite／Debug DB，来源页与返回 |
| MIME 模态 | `ModalityCategoryTest`、列表／详情／Atlas Mapper、相关 Query／ViewModel | 真实字节经生产映射覆盖列表／详情／对比／筛选；Atlas 集成验证缓存／收藏无须联网或改原值 |
| 参数量 | `AtlasParametersTest`、`ModelParameterFormattingTest`、详情 Mapper、`CatalogTypeConvertersTest`、快照／仓库／Debug Schema | `ModelParametersScreenTest`、Atlas／Catalog／Favorite／Debug DB、详情／对比及返回 |
| 榜单 | `BenchmarkBoardMapperTest`、`BenchmarkChartTest`、`BenchmarkResultPresentationTest`、`BenchmarkViewModelTest` | `BenchmarkRankingDatabaseTest`、`BenchmarkScreenTest`、`BenchmarkReentryTest`；入口或底栏变化加外壳／导航／详情返回 |
| 导航／共享转场 | `NavigationGeometryTest`、`NavigationScrollBehaviorTest`、`NavigationScrollTest`、`NavigationScrollReducedMotionTest`、`ModelAtlasAppTest` | `ModelDetailNavigationTest`、`ModelSharedTransitionTest`／`ModelSharedTransitionReducedMotionTest`、`ModelSeekableTransitionTest`，按影响加收藏／RTL／恢复 |
| Debug 数据库／双版本 | `DebugDatabaseSchemaTest`、`DebugDatabaseViewModelTest`、`DebugDatabaseStoreTest`、`DebugDatabaseScreenTest`、`DebugFeatureTest` | 外壳、真实 Room 冲突／级联／重开；构建隔离／安装变化按[双版本说明](debug-release-coexistence.md)检查 Release |
| 旧评测协议 | `BenchmarkUpdateTest`、`BenchmarkSyncRunnerTest`、`BenchmarkHttpSourceTest`、`BenchmarkIntegrationTest` | 仅兼容路径变动时运行，不作为当前 Atlas／榜单固定套件，见[旧契约](archive/legacy-benchmarks.md) |

### 关键断言（不是执行记录）

- 字段／存储：null、空、false、零不混淆；规范模型／服务分开；回滚、文件重开、并发收藏、显式关联；编译 Schema fake 不能替代真实 SQLite 测试。
- 参数量：BigDecimal 高精度往返，真实稠密／MoE／仅激活／仅组件／未披露，不推算；非法数值／来源拒绝；UI 只显示总量／激活量，切标签／替换／恢复正确。
- 详情评测：数值一位小数与文本原样、英文 A–Z、同名按未舍入数值降序、平分稳定、文本／缺失置后、全部配置及下标身份保留；不要把 UI 舍入值当原值。
- 榜单：同实际版本／指标／单位、每模型最高原值、同分并列、下架／非 LLM／未匹配过滤；文本／未知方向／低分更优不在榜单展示但详情原记录仍保留；局部轴边界、品牌色、pts／完整单位读屏、模型名后档位。
- 榜单恢复：用延迟首个结果的 Repository 和真实 Route／ViewModel，检查双向滑入、底栏切换、连续往返及生命周期恢复期间不插入 loading、不丢卡片／阅读位置，随后应用最新本地结果；成功空榜单不退回 loading，失败清旧数据、重试显示加载，离屏无订阅，完整榜单按钮／系统返回不重建订阅。
- 完整榜单简介／弹窗：浅深约 64dp 简介与完整名称，信息按钮至少 48dp，说明默认不占正文、空白无入口、长说明可滚、重建／系统返回仅关弹窗、阅读不变，320dp 双倍字号 RTL，整卡进入／返回。
- 收藏：可用项取消后仍保留普通目录卡、返回交接后才重排；下架项先持久化删除、返回后淡出；保存失败不返回。后台目录变化不改本次收藏身份／可用性，用户收藏增删实时生效，显式目录发布才应用新快照。
- Debug 编辑返回：区分 IME 可见／隐藏；键盘消费返回不等于退出编辑。取消确认后焦点可能恢复并重新打开 IME，测试应明确键盘前提，校验草稿保留及放弃不写库。
- UI：真实文字边界与像素，浅深／320dp／双倍字号／RTL、键盘／安全区／末尾可达；转场中间帧、暂停／取消／交接与零动画，不只检查最终截图。
- HTTP source 注入响应不等于线上 HTTPS 验收；语义／触摸探索状态注入不等于真实 TalkBack。模拟器不替代真机视觉、系统边缘返回、动态色、备份／迁移服务。

## 单元测试一次选择多个类

在仓库根执行，替换为本轮类／方法；单元方法用 `类名.方法名`：

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'io.github.chenlongapps.modelatlas.ui.modellist.ModelListQueryTest' \
  --tests 'io.github.chenlongapps.modelatlas.ui.modellist.ModelListViewModelTest'
```

## 一次构建、安装，批量 instrumentation

使用已运行、满足当前 minSdk 33 的设备，先 `adb devices -l` 核对序列号。以下两类仅为命令示例：

```sh
export ANDROID_SERIAL=emulator-5554 # 替换实际设备
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class 'io.github.chenlongapps.modelatlas.ui.modellist.ModelCatalogControlsTest,io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailScreenTest' \
  io.github.chenlongapps.modelatlas.debug.test/androidx.test.runner.AndroidJUnitRunner
```

- 源码／测试未变可复用本轮 APK、只换 runner 范围；改代码／资源／测试／依赖后须重建安装对应产物，不拿旧 APK 验证新代码。
- 不固定双跑 Gradle 筛选和显式 runner。本机曾有 Gradle class 参数范围不完整问题，以 runner 实际类／数量为准，不用 Gradle 成功代替核对。
- 不常规 clean、清数据、卸载或重建／冷启动模拟器。环境挂起再排障，涉及丢数据先确认；不通过全局关闭动画／删断言提速。构建和独立 Lint 曾遇 KSP 生成文件竞争，必要时分开执行，不以 clean 为默认解决办法。

失败先定向重跑，instrumentation 方法用 `类名#方法名`：

```sh
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class 'io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailNavigationTest#opensFromCardHidesMainContentAndBothBackActionsRestorePosition' \
  io.github.chenlongapps.modelatlas.debug.test/androidx.test.runner.AndroidJUnitRunner
```

定位／修复后补受影响类／链路，不无条件重跑无关全套；设备中断只计有明确结束结果的用例，补未完成范围，不能记整批通过。

### 定向截图与预测性取消诊断

- `BenchmarkScreenTest` 可在同一次 runner 中传 `-e captureRegression true`，将简介浅深色、说明弹窗、长说明末尾与窄屏双倍字号 RTL 截图保存到 Debug 应用的外部专属目录 `files/regression-screenshots/`；普通执行不截图，不自动更新任何视觉基线。截图用于人工检查，不替代布局／状态断言。
- 预测性取消像素用例每帧只截图一次，Logo 与容器使用同一张图。像素检查失败时，将最近三帧及比例、帧号、Transition 状态保存在同目录体系的 `files/predictive-failures/<时间戳>/`，日志标签为 `PredictiveCancelTest`。取回证据时同步保留 runner 与设备日志；截图超时不能当作 Logo 缺失，正常截图中的缺失也不能归咎于环境。
- 同 APK 连续定向通过只说明本轮未复现；有根因与对应修复证据才能称为已修复。批次通过及单跑通过分别记录，不抹掉原失败。

## 结果核对与发布

每次记录：
1. **范围／版本**：改动、实际类／方法、设备 API、APK 与源码是否一致。
2. **真实结果**：类名、`numtests`、完成数量、最终 OK／失败／忽略；0 项、缺类、中断、崩溃均不是范围通过，源码文件数也不是执行类数。
3. **证据／边界**：报告或 runner 输出、首次失败与复跑、未执行及原因；显式 adb 不更新 Gradle 报告，不能只凭退出码／历史数量判定。临时路径不保证长期可用。

TODO 只留未闭环项；已完成变更的日期、执行结果与证据写[历史](archive/development-history.md)，不把日志复制到架构／设计说明。

发布前执行全量单元、Debug Lint、全量 instrumentation 和必要真机验收；Release／签名相关范围按 AGENTS 补，不以日常选测代替。普通 UI 小改只补受影响且自动断言不足的视觉检查，不固定巡检所有页；未实际重新运行就不更新为“已验证”。
