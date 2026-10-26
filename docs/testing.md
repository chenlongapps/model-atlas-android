# 分级测试与定向回归

**状态：已采用分级验证规则。** 不删除现有测试、不更换框架；本次仅调整验证流程，不代表应用测试已重新通过，也未测量节省时长。环境要求、分级边界和构建规范以 [AGENTS.md](../AGENTS.md#构建与验证) 为准，既有覆盖见[架构说明](architecture.md#现有单元测试)。

## 1. 先选范围，再执行

每轮先列出改动影响、拟执行的类／方法及扩大范围的原因，不从全量模拟器测试开始。

| 改动 | 优先选择 | 需要补充时 |
| --- | --- | --- |
| 纯文档 | 链接、命令和描述一致性检查 | 不启动模拟器、不构建应用 |
| 搜索／筛选 | `ModelListQueryTest`、`ModelListViewModelTest` 中相关用例；UI 变化加 `ModelCatalogControlsTest` | 卡片／列表状态变化加 `ModelListScreenTest`；切页恢复变化加外壳相关用例 |
| 详情字段／布局 | `ModelDetailMapperTest`、`ModelDetailViewModelTest` 中相关用例及 `ModelDetailScreenTest` | 入口／返回／恢复加 `ModelDetailNavigationTest`；共享身份或容器布局变化加 `ModelSharedTransitionTest` 相关用例 |
| 对比 | `ModelCompareQueryTest`、`ModelCompareViewModelTest`、`ModelCompareScreenTest` 中相关用例 | 默认报价规则变化再覆盖价格映射及列表展示 |
| 收藏 | `FavoritesViewModelTest`、`FavoriteRemovalTransitionTest`、`FavoritesScreenTest` 中相关用例 | 存储变化加 `FavoriteDatabaseTest`；返回动画变化加共享转场及零动画用例 |
| 外观／设置 | `AppearanceViewModelTest`、`AppearanceColorTest`、`SettingsRepositoryTest` 及对应界面用例 | 全局主题变化覆盖 `ModelAtlasThemeTest` 和主要页面浅深色；持久化／备份变化补对应测试 |
| 数据解析／映射／仓库 | 对应 Parser、Mapper、Repository 单元测试 | Room 查询／事务变化加 `CatalogDatabaseTest`／`FavoriteDatabaseTest`；共享字段变化覆盖所有受影响消费者 |
| 全局导航／共享转场 | `ModelAtlasAppTest`、`ModelDetailNavigationTest`、`ModelSharedTransitionTest` 中相关用例 | 按影响补收藏来源、预测性返回、零动画、RTL／状态恢复；大范围修改扩大回归 |

这是选测起点，不是固定打包执行的套件。小改可定位到方法，功能完成应覆盖相关类及受影响的跨页面链路；影响不明确时扩大范围，不因表中未列出而跳过受影响测试。

例如筛选与详情字段精简：先跑查询、列表 ViewModel 和详情映射单元测试，再跑筛选及详情 Screen 测试，补详情进入／返回用例；只有影响共享身份／布局时才追加相应转场用例，不自动重跑无关收藏、对比、外观和调试页。

## 2. 单元测试一次选择多个类

在仓库根目录执行；把类名替换为本次范围，支持 `类名.方法名` 定向选择：

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'io.github.chenlongapps.modelatlas.ui.modellist.ModelListQueryTest' \
  --tests 'io.github.chenlongapps.modelatlas.ui.modellist.ModelListViewModelTest' \
  --tests 'io.github.chenlongapps.modelatlas.data.mapper.ModelDetailMapperTest'
```

## 3. 模拟器一次构建、安装，批量执行

使用已经运行且满足当前 `minSdk` 的设备／模拟器。先用 `adb devices -l` 确认序列号，再显式指定，避免多设备时跑错目标。以下以两组页面测试为例，不表示必须执行这两组：

```sh
export ANDROID_SERIAL=emulator-5554 # 替换为实际序列号
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class 'io.github.chenlongapps.modelatlas.ui.modellist.ModelCatalogControlsTest,io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailScreenTest' \
  io.github.chenlongapps.modelatlas.debug.test/androidx.test.runner.AndroidJUnitRunner
```

- 本轮 APK 与源码／测试一致时，后续换类或重跑只执行最后一条 runner 命令。修改源码、资源、依赖或测试后必须重新构建并安装，不能用旧 APK 验证新代码。
- 不把 Gradle 筛选执行再加显式 runner 执行作为固定双跑；已通过 runner 核对范围就不重复执行同一批。
- 保留原有模拟器和应用数据，不常规冷启动、卸载、清数据或执行 `clean`。出现设备离线／挂起时再针对环境排障；涉及丢弃数据必须先确认。
- 像素、转场及零动画测试保留各自的动画设置要求，不通过全局关闭动画或删断言提速。

### 失败后只重跑对应方法

runner 的方法筛选使用 `类名#方法名`，例如：

```sh
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class 'io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailNavigationTest#opensFromCardHidesMainContentAndBothBackActionsRestorePosition' \
  io.github.chenlongapps.modelatlas.debug.test/androidx.test.runner.AndroidJUnitRunner
```

定向复跑用于定位或验证修复，不抹去首轮失败；修复后补跑受影响类／链路。设备中断时只认定已明确完成的用例，未完成范围需补跑，不把中断整批记为通过。

## 4. 发布前与结果核对

发布前使用 [AGENTS.md](../AGENTS.md#构建与验证) 中的全量命令；涉及 Release 的验证按该文件规定补充，不以选测代替发布回归。真实系统返回、TalkBack、备份／迁移、跨厂商动态色等仍按 [TODO.md](../TODO.md#待验收与发布) 补真机验收，不因模拟器通过而勾选完成。

每次记录至少包含：

- **范围与版本**：本次影响、实际执行类／方法、设备 API，以及 APK 是否对应当前源码。
- **实际结果**：核对 runner 的实际类名、`numtests`、结束用例数量与最终 `OK`／失败／忽略信息。源码文件可能包含多个测试类；不要以文件数推断执行类数。过滤到 0 项、缺类、失败、崩溃或中断均不能记为范围通过；有预期跳过项时单独说明。
- **证据与边界**：保留当次 Gradle 报告或 runner 输出，说明失败及复跑、未执行范围与原因，不仅凭进程退出码、Gradle 成功或历史数量认定通过。显式 `adb` 执行不会自动更新 Gradle 的测试报告，应以当次 runner 输出为准。

常规 UI 小改不附带全功能手动巡检或逐页截图；只对受影响且自动断言不足的布局、交互、动画补视觉检查。需要评估后续提速收益时，分别记录构建、安装和用例执行耗时，再决定是否优化慢测试或引入其他工具。
