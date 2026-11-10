# Debug / Release 双版本共存

本文维护安装身份、签名与编译隔离；实际设备验收状态只在 [TODO](../TODO.md#待验收与发布)，历史构建／模拟器结果见[归档](archive/development-history.md#7-debug-调试双版本与许可说明)。

## 安装身份与隔离

配置入口为 [app/build.gradle.kts](../app/build.gradle.kts) 和 `app/src/debug/res/`。

| 属性 | Release | Debug |
| --- | --- | --- |
| 名称 | Model Atlas | Model Atlas Debug |
| Application ID | `io.github.chenlongapps.modelatlas` | `io.github.chenlongapps.modelatlas.debug` |
| Version Name | 当前 `1.0` | 基础版本加 `-debug` |
| 可调试 | 否 | 是 |
| 图标 | 原版 | 普通／圆形／单色均有 DEBUG 绶带 |
| 底栏 | 模型／评测／对比／设置 | 追加同级调试，不放设置入口 |

- Debug 用 `applicationIdSuffix = ".debug"`、`versionNameSuffix = "-debug"`，不改 namespace／Kotlin 包名／Release 安装身份，不引入 flavors 或新模块。
- 图标原白底图形右上叠绿色 45° 绶带和白色 `DEBUG`，主图略下移避让；由桌面遮罩裁边、文字在安全区，单色图层保留角标但颜色由系统决定。当前 minSdk 33，自适应图标覆盖即可，不能按旧 API 29 文案安排安装验收。
- `main` 只依赖统一 `ui.debug.DebugFeature` 接口；Debug source set 提供导航条目／Route／Screen／ViewModel／数据库工具与专用资源，Release 同签名空实现、条目 null。没有 `BuildConfig.DEBUG` 运行时分支，Release 不编译真实工具／专用资源。
- 两 Application ID 各自沙盒，Room 无需改名或 Schema。旧原包名 Debug 的数据仍属原应用，不自动迁移到新 Debug；不能为了安装而卸载旧应用或清数据。
- 备份身份隔离不等于备份／迁移服务已验证；外观文件单独排除见[外观说明](appearance-settings.md#存储和备份边界)。未来自定义 Provider authorities 用 `${applicationId}`，不要写死 Release 包名；当前主 Manifest 无自定义 Provider，也须检查依赖合并项。

调试页统计／刷新与子页导航见[架构](architecture.md#顶层分页与子页面)，数据库五表 CRUD 的安全／覆盖边界只在[本地数据](local-data.md#debug-数据库管理)维护。

## 构建与安装

环境与选测以 [AGENTS.md](../AGENTS.md#构建与验证) 为准，先 `adb devices -l` 确认目标，多设备时指定 `-s`：

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease
```

Release 签名只从本机 `~/.gradle/gradle.properties` 按需读取，不是仓库中的 properties；以下为占位配置，不提交密钥、实际路径或密码：

```properties
modelAtlas.release.storeFile=/absolute/path/to/release.jks
modelAtlas.release.storePassword=你的密钥库密码
modelAtlas.release.keyAlias=你的密钥别名
modelAtlas.release.keyPassword=你的密钥密码
```

密码在本机明文保存，应限制文件权限、安全备份密钥和密码。配置 storeFile 后其余三项须齐全；使用 Release 密钥，不回退 Debug 签名，随后可 `./gradlew :app:installRelease`。

未配置 storeFile 时输出未签名 APK（通常 `app/build/outputs/apk/release/app-release-unsigned.apk`，以产物为准），无 `installRelease` 任务。也可用 Android Studio Generate Signed Bundle / APK 或安全签名流程；构建成功不等于可安装，覆盖已有 Release 必须原签名，签名冲突不直接卸载／清数据。

安装后核对：

```sh
adb shell pm list packages | grep io.github.chenlongapps.modelatlas
```

预期同时存在 `io.github.chenlongapps.modelatlas` 与 `io.github.chenlongapps.modelatlas.debug`。

## 变更后的验证边界

1. 改构建／资源／隔离时检查合并 Manifest 与 APK 的包名、版本、名称、可调试状态、Startup authorities；Release 不含真实 Debug 类、`debug_*` 文案或专用图标。
2. 设备检查两个入口、独立启动、核心链路及数据互不影响；Debug 普通／圆形遮罩不裁 DEBUG 文字，主题图标角标可辨。
3. 验证更新 Debug 不覆盖 Release；卸载隔离只在确认 Debug 数据可丢弃后人工操作，不自动执行破坏性验收。
4. 按改动选测及发布要求补测试，当前设备须 API 33+；模拟器／资源隔离通过不等于签名共存、真机视觉或 TalkBack 验收完成。
