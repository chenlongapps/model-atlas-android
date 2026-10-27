# Debug / Release 双版本共存

**状态：已实施，待设备验证。** 验证进度见 [TODO.md](../TODO.md)，完成实际设备验证后再标记为“已验证”。

## 当前实现

通过 [app/build.gradle.kts](../app/build.gradle.kts) 的 Debug 构建类型后缀与 [Debug 资源覆盖](../app/src/debug/res/values/strings.xml) 区分安装身份和桌面名称：

| 属性 | Release | Debug |
| --- | --- | --- |
| 应用名称 | Model Atlas | Model Atlas Debug |
| 启动图标 | 原版图标 | 右上角绿色斜向绶带、白字 `DEBUG` |
| Application ID | `io.github.chenlongapps.modelatlas` | `io.github.chenlongapps.modelatlas.debug` |
| Version Name | `1.0` | `1.0-debug` |
| 可调试 | 否 | 是 |

Debug 配置为 `applicationIdSuffix = ".debug"`、`versionNameSuffix = "-debug"`，通过 `app/src/debug/res/` 覆盖 `app_name` 和普通／圆形自适应启动图标。调试工具代码及专用资源也放在 Debug source set，其余代码和资源复用主源码集。Manifest 继续使用 `android:label="@string/app_name"`。

图标保留主源码集的白色背景与原图形，在右上角叠加向右下方倾斜 45° 的绿色绶带与白色 `DEBUG` 字样，不使用圆角标签。绶带延伸到图标边缘，由桌面遮罩裁切两端，文字位于安全区；原图形等比例保持尺寸，略向下平移以避开绶带。角标使用矢量轮廓，文字镂空透出白底；单色图层复用同一角标，系统主题图标下仍可识别 `DEBUG`，颜色由系统主题决定，不保证绿色。项目最低 API 29 使用自适应图标，无需覆盖旧版 PNG；Release 图标资源不变。

未修改 `namespace`、`defaultConfig.applicationId`、Kotlin 包名、主 Manifest 或 Release 优化配置；没有引入 Product Flavors 或新模块。Release 签名通过本机 Gradle 属性按需配置，不在仓库中保存密钥或密码，也不回退使用 Debug 签名。

## 数据与兼容边界

- 不同 Application ID 由 Android 分配独立应用数据空间，Room3 数据库无需改名，也无需修改 Schema、DAO 或 Hilt 配置。
- Release 安装身份和升级路径保持不变；此前使用原 Application ID 安装的旧 Debug 不会自动迁移到新的 Debug 身份，其数据仍属于原应用。不要为了安装新 Debug 而卸载原应用或清除其数据。
- 原有备份策略保持不变（`allowBackup="true"`）。备份身份分离不代表已验证云备份和设备迁移行为。
- Debug 使用专用图标，底部导航为“模型｜收藏｜对比｜设置｜调试”；调试页与另外四页同级，不再位于设置中，不显示返回按钮，始终保留底栏。页面提供应用／设备信息、三张目录表全量数量（不包含收藏表）、复制诊断信息与手动刷新。顶层统计非实时，仅切入停稳或手动操作后读取；刷新复用现有仓库与服务器限流，成功后同步列表状态。独立数据库管理子页另支持四表逐条 CRUD；服务商删除级联、收藏删除不可撤销，目录同步可能覆盖修改，不提供批量清空或任意 SQL。详见[本地目录数据](local-data.md#debug-数据库管理)。
- `main` 只调用 `ui.debug.DebugFeature` 的统一接口；`debug` 提供导航条目、完整页面及 ViewModel，`release` 的导航条目为 null、内容为空，保持“模型｜收藏｜对比｜设置”四栏。调试资源仅置于 `src/debug/res/`，Release 不编译真实调试页面、ViewModel 或调试专用资源，不依赖运行时 `BuildConfig.DEBUG` 判断。
- 主 Manifest 没有自定义 ContentProvider。以后添加 FileProvider 时，authorities 应使用 `${applicationId}.fileprovider`，不要写死 Release 包名；依赖合并的 Provider 也应确认不会发生冲突。

## 构建与安装

环境要求和构建规范以 [AGENTS.md](../AGENTS.md) 为准。Debug 构建：

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release 构建：

```sh
./gradlew :app:assembleRelease
```

Release 签名从本机 `~/.gradle/gradle.properties` 读取，添加以下属性（将占位值替换为实际配置，密钥路径使用绝对路径）：

```properties
modelAtlas.release.storeFile=/absolute/path/to/release.jks
modelAtlas.release.storePassword=你的密钥库密码
modelAtlas.release.keyAlias=你的密钥别名
modelAtlas.release.keyPassword=你的密钥密码
```

该文件位于仓库外，不是项目中的 `gradle.properties` 或 `local.properties`；密码以明文保存，应限制本机文件权限并安全备份密钥库及密码。仓库只提交读取属性的代码，不提交实际路径、密钥库或密码。

提供 `modelAtlas.release.storeFile` 后，其他三项必须同时配置，Release 使用该密钥签名，可执行：

```sh
./gradlew :app:installRelease
```

未提供 `modelAtlas.release.storeFile` 时保留未签名构建，通常输出 `app/build/outputs/apk/release/app-release-unsigned.apk`，以实际产物为准，此时没有 `installRelease` 任务。构建成功不代表可直接安装，也可使用 Android Studio 的 **Generate Signed Bundle / APK** 或安全的签名流程生成已签名 APK。覆盖已有 Release 必须使用原签名；不要因签名冲突直接卸载或清除数据。

两版均安装后检查：

```sh
adb shell pm list packages | grep io.github.chenlongapps.modelatlas
```

预期同时存在：

```text
package:io.github.chenlongapps.modelatlas
package:io.github.chenlongapps.modelatlas.debug
```

## 分级验收

引入对比页之前，顶层调试页构建隔离已单独验证：Debug／Release 均能组装，207 项 Debug 单元测试和 Debug lint 通过；APK 检查确认 Debug 包含当时的第三栏导航文案、真实调试页面及专用资源，Release 只有空 `DebugFeature`，没有 `DebugScreenKt`、`DebugViewModel`、调试状态模型、`debug_*` 文案或 `ic_debug` 图标。此结果不替代下述双版本共存与签名设备验收。

引入对比页之前，顶层调试页已在 Android 17／API 37 模拟器分别通过 `DebugFeatureTest` 11 项及 `ModelAtlasAppTest` 24 项，覆盖当时的三栏导航、恢复、模型双击边界与原有主界面回归；恢复首帧保存实测底栏高度，避免列表末尾位置偏移。真实应用启动后另通过 UI 树确认同级“调试”选中态、保留底栏、无返回按钮及实际信息／统计读取。真机无线 ADB 曾在执行中断开，仍需稳定连接后补验；模拟器结果不等于双版本共存与签名设备验收完成。当前四栏／五栏导航及收藏的回归范围见 [TODO.md](../TODO.md)。

1. 构建 Debug，核对 APK 中的 Application ID、版本名称、应用名称和可调试标志；核对 Release 合并产物仍使用原安装身份与名称。
   同时检查 Debug 普通／圆形图标均带有右上角绿色斜向绶带，Release 图标不变；设备上验证圆形／圆角遮罩下 `DEBUG` 文字无裁切，以及启用主题图标后字样仍可见。
2. 使用实际设备上的已签名 Release 与 Debug 验证双入口、分别启动、更新 Debug 不覆盖 Release，以及卸载 Debug 不影响 Release 及其数据。卸载前确认 Debug 数据可丢弃，不自动执行破坏性检查。
3. 分别验证模型列表、网络加载、Room3 本地读写、搜索筛选、详情进入与返回、共享元素动画及设置页面；确认两版本地目录状态独立。
4. 构建与基础运行验证后执行 `:app:testDebugUnitTest`、`:app:lintDebug`；必要时在 API 29 及以上设备执行 `:app:connectedDebugAndroidTest`。仅在出现构建、资源或安装问题时扩大回归范围。

设备共存、数据隔离及核心功能均验收后，才可将本文状态改为“已验证”。
