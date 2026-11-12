# 分级测试与定向回归

构建环境、分级要求与发布命令以 [AGENTS.md](../AGENTS.md#构建与验证) 为准；本文维护选测入口、执行方式及结果判定，不记录历史通过总数。执行状态见 [TODO](../TODO.md#待验收与发布)，以往记录见[历史](archive/development-history.md)。

## 先选范围

先列影响与拟执行类／方法，小改可选方法，功能完成补相关类及导航／返回链路；共享组件、全局主题／导航、存储、依赖或构建的大范围修改扩大回归。以下是起点，不是每次全跑的套件；测试源码为 `app/src/test/`、`src/testDebug/`、`src/androidTest/`、`src/androidTestDebug/`。

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
| LLM 请求 | `OpenAiCompatibleClientTest`（序列化／SSE／超时／取消／脱敏／资源关闭）、`LlmLiveTestConfigTest`（环境／本地配置优先级） | `LlmLiveTest` 读取环境变量或 `local.properties`，必须用 `-PllmLiveTest=true` 显式开启；默认跳过不计线上通过，命令及密钥边界见 [LLM API](llm-api.md#测试) |
| AI 聊天 | `ChatViewModelTest`（Fake 客户端、上下文／增量／取消／重试／防重复／配置校验），`ChatScreenTest`、`ChatNavigationTest` | 复用客户端测试回归真实 SSE／socket 资源释放；导航加 `ModelAtlasAppTest`、`AppearanceNavigationTest`、`ModelDetailNavigationTest`，IME 首次返回必须不离页；不默认执行真实付费 API |
| AI 配置存储 | `LlmConfigRepositoryTest`、`ChatViewModelTest`、设备 `KeystoreConfigTest` | `ChatScreenTest`／`ChatNavigationTest`、`AppearanceBackupRulesTest`；真实 DataStore 文件重开、无明文、篡改／丢失密钥、显式恢复损坏文件、保存失败保留；只用随机测试 Keystore 别名，禁止清除生产凭据 |
| Atlas 发布包 | `AtlasReleaseLoaderTest`、`AtlasHttpSourceTest`、`AtlasCatalogMapperTest`、`ModelRepositoryTest` | `AtlasCatalogIntegrationTest`，共享存储加 Catalog／Favorite／Debug DB，来源页与返回 |
| 来源页／许可 | `ModelAtlasAppTest` 中 `settingsOpensStandaloneSourcePageWithPricesAndBundledLicense` 及三个 `sourcePage*` 方法 | 检查实际应用 assets、当前 Atlas 说明及 Models.dev MIT，按钮／系统返回、阅读位置恢复和大字 RTL |
| MIME 模态 | `ModalityCategoryTest`、列表／详情／Atlas Mapper、相关 Query／ViewModel | 真实字节经生产映射覆盖列表／详情／对比／筛选；Atlas 集成验证缓存／收藏无须联网或改原值 |
| 参数量 | `AtlasParametersTest`、`ModelParameterFormattingTest`、详情 Mapper、`CatalogTypeConvertersTest`、快照／仓库／Debug Schema | `ModelParametersScreenTest`、Atlas／Catalog／Favorite／Debug DB、详情／对比及返回 |
| 榜单 | `BenchmarkBoardMapperTest`、`BenchmarkChartTest`、`BenchmarkResultPresentationTest`、`BenchmarkViewModelTest` | `BenchmarkRankingDatabaseTest`、`BenchmarkScreenTest`、`BenchmarkRefreshTest`、`BenchmarkReentryTest`、`BenchmarkSharedTransitionTest`／`BenchmarkSharedTransitionReducedMotionTest`；刷新协调加 `ModelRepositoryTest`／模型列表 ViewModel，共用提醒加 `CatalogStatusNoticeTest`；入口或底栏变化加外壳／导航／详情返回 |
| 导航／共享转场 | `NavigationGeometryTest`、`NavigationScrollBehaviorTest`、`NavigationScrollTest`、`NavigationScrollReducedMotionTest`、`ModelAtlasAppTest` | `ModelDetailNavigationTest`、`ModelSharedTransitionTest`／`ModelSharedTransitionReducedMotionTest`、`ModelSeekableTransitionTest`，按影响加收藏／RTL／恢复 |
| Debug 数据库／双版本 | `DebugDatabaseSchemaTest`、`DebugDatabaseViewModelTest`、`DebugDatabaseStoreTest`、`DebugDatabaseScreenTest`、`DebugFeatureTest` | 外壳、真实 Room 冲突／级联／重开；构建隔离／安装变化按[双版本说明](debug-release-coexistence.md)检查 Release |

### 测试维护边界

- 沿用 JUnit4、协程虚拟时间／显式 fake、设备 Compose 语义与像素断言、真实 Room／SQLite 集成；不额外引入 Mock、Robolectric 或截图插件。Hilt 提供生产依赖，Screen 测试继续直接传状态，Route 测试使用已有工厂，不为清理改写测试架构。
- 旧整数版本评测的三类单元、一类集成、v2/v3 样本与归属许可均已删除；Models.dev 测试只保留当前服务商接口，规范模型／成绩用 Atlas DTO 与发布包。不保留仅为废弃协议服务的辅助代码。
- Room KSP 直接检查生产数据库；不再复制一份未使用的测试数据库或单独配置 Room 的 `kspTest`。真实事务、回滚、文件重开、收藏保留由当前 Atlas／Catalog／Favorite 集成测试覆盖。
- 只合并同一行为的重复断言／准备逻辑；业务单元、数据库事务与设备 UI 属于不同边界，不因名称类似就删掉。当前仍使用的价格条件、查询恢复、MIME 投影、Atlas 缓存关联修正及零动画回归继续保留。
- 不保留低于应用 minSdk、永远无法执行的设备用例。发现数量与源码不符时可用 runner 的 `-e log true` 列举实际用例并核对 SDK 过滤；该模式不运行测试，即使返回 `OK` 也不能作为通过证据。
- 页面导航测试先满足当前可见性规则：目录向下浏览隐藏底栏后，反向轻滑唤回再点击，不通过未合并语义树命中不可见导航来绕过行为。

### 关键断言（不是执行记录）

- 字段／存储：null、空、false、零不混淆；规范模型／服务分开；回滚、文件重开、并发收藏、显式关联；生产 KSP 编译不能替代真实 SQLite 测试。
- 参数量：BigDecimal 高精度往返，真实稠密／MoE／仅激活／仅组件／未披露，不推算；非法数值／来源拒绝；UI 只显示总量／激活量，切标签／替换／恢复正确。
- 详情评测：数值一位小数与文本原样、英文 A–Z、同名按未舍入数值降序、平分稳定、文本／缺失置后、全部配置及下标身份保留；不要把 UI 舍入值当原值。
- 榜单：明确版本按实际版本隔离，`unspecified` 定义接受缺失／不同版本并合榜，指标／单位／唯一关联仍严格；每模型最高原值、同分并列、下架／非 LLM／未匹配过滤；文本／未知方向／低分更优不在榜单展示但详情原记录仍保留。旧 Atlas 缓存离线通过榜单或详情入口修复关联，版本／配置／收藏／release 不变、歧义不套定义、手工成绩不变及文件重开；真实随包 HLE 为 84 个数值 LLM 模型（仅当前快照）。局部轴边界、品牌色、pts／完整单位读屏、模型名后档位。
- 评测热度：`popularity` 数字／0／缺失／null 的解析与存储往返、错误类型及非有限值整包拒绝；降序、缺失最后、同值稳定兜底，兼容实际版本继承热度；仅热度变化须更新快照及卡片顺序，模型排名及榜单 ID 不变，文件重开仍保留。
- 榜单恢复：用延迟首个结果的 Repository 和真实 Route／ViewModel，检查双向滑入、底栏切换、连续往返及生命周期恢复期间不插入 loading、不丢卡片／阅读位置，随后应用最新本地结果；成功空榜单不退回 loading，失败清旧数据、重试显示加载，离屏无订阅，完整榜单按钮／系统返回不重建订阅。
- 评测刷新：真实下拉／读屏动作、首次读取与重复刷新防重入，刷新不换 loading／不主动回顶；成功等提交后本地读完成，覆盖有变化／无变化／空结果，网络或主动读取失败保留内容，失败重试／关闭、浅深提示、无障碍超时及旧计时不清新错误。模型启动同步／评测／Debug 并发只请求一轮，共享成功及失败；等待者取消不影响发起者，发起者取消释放等待者且可重试。真实 Route 刷新后进入／返回榜单不重建持续订阅。
- 完整榜单简介／弹窗：浅深约 64dp 简介与完整名称，信息按钮至少 48dp，说明默认不占正文、空白无入口、长说明可滚、重建／系统返回仅关弹窗、阅读不变，320dp 双倍字号 RTL，整卡进入／返回。
- 评测共享转场：整卡中间帧及背景不淡出、Logo／名称以完整榜单 ID 隔离、宽字标等比、入场中返回、左右边缘预测性暂停／取消／100% 确认、零动画。滚动后停身份匹配、退出不强制回顶；重建及返回取消保留榜单位置，完整退出交接后清理，按钮／系统／预测性确认退出后再次打开均从顶部共享进入，总览位置不变；覆盖轻微偏移与深滚动、成绩共享中间帧。来源移除退化淡出、错误不留旧成绩。公共控制器变化同时回归模型共享转场；dispatcher 注入不替代系统边缘手势验收。
- 成绩共享：前三名的名称／分数／轨道／填充按榜单＋模型 ID＋角色隔离，同名／并列不串动画；加入第四名改变坐标轴的样本，按品牌色像素验证填充的真实端点及中间帧边界、轨道可见性、6→8dp 条高、暂停／取消及返回归位。保持原始分数／各页轴与 `pts`／`points` 端点格式，零长度填充不补造值；窄屏大字 RTL 换行、真实段落宽高不超过文字布局、滚动后停匹配及第四名不共享。检查原有柱条颜色／读屏／卡片点击语义不回退。
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
- `BenchmarkSharedTransitionTest` 同样支持该开关，保存浅／深主题来源卡片、展开中间帧、完整榜单与收缩中间帧，文件名前缀为 `benchmark-transition-`；成绩专项中间帧使用 `benchmark-results-` 前缀。预测性取消逐帧断言复用每帧同一张 Compose root 截图检查容器边界与 Logo 可见性。
- 预测性取消像素用例每帧只截图一次，Logo 与容器使用同一张图。像素检查失败时，将最近三帧及比例、帧号、Transition 状态保存在同目录体系的 `files/predictive-failures/<时间戳>/`，日志标签为 `PredictiveCancelTest`。取回证据时同步保留 runner 与设备日志；截图超时不能当作 Logo 缺失，正常截图中的缺失也不能归咎于环境。
- 同 APK 连续定向通过只说明本轮未复现；有根因与对应修复证据才能称为已修复。批次通过及单跑通过分别记录，不抹掉原失败。

## 结果核对与发布

每次记录：
1. **范围／版本**：改动、实际类／方法、设备 API、APK 与源码是否一致。
2. **真实结果**：类名、`numtests`、完成数量、最终 OK／失败／忽略；0 项、缺类、中断、崩溃均不是范围通过，源码文件数也不是执行类数。
3. **证据／边界**：报告或 runner 输出、首次失败与复跑、未执行及原因；显式 adb 不更新 Gradle 报告，不能只凭退出码／历史数量判定。临时路径不保证长期可用。

TODO 只留未闭环项；已完成变更的日期、执行结果与证据写[历史](archive/development-history.md)，不把日志复制到架构／设计说明。

发布前执行全量单元、Debug Lint、全量 instrumentation 和必要真机验收；Release／签名相关范围按 AGENTS 补，不以日常选测代替。普通 UI 小改只补受影响且自动断言不足的视觉检查，不固定巡检所有页；未实际重新运行就不更新为“已验证”。
