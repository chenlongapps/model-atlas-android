# 外观设置

「设置 → 外观」分别选择明暗和颜色来源，默认跟随系统并使用系统动态色。视觉见[设置设计](page-design.md#设置与-debug)，显示内容见[页面字段](page-information.md#设置页)。

## 底栏偏好

「底部导航栏自动隐藏」默认关闭，已有设置缺少该字段也是关闭。开关即时生效并保存；显隐、触摸探索、子页与助手规则见[悬浮底部导航](page-design.md#悬浮底部导航)。

## 配色与预览

- `ui/theme/Theme.kt` 统一解析应用主题与色板。动态色不可用时用 Material 3 固定备用方案；预设和自定义不叠加动态色。MaterialKolor 使用 Tonal Spot，低饱和度预设用 Neutral。最初的窗口启动底色由 DayNight 资源决定。
- 自定义色相为 0–360°；已有历史色相时点击即启用并打开取色器，没有时从 220° 开始，首次移动才启用。切换明暗、系统色或预设不清除历史色相。
- 拖动实时预览，停顿 300ms 或松手保存，关闭取色器／Activity 暂停时主动提交。离开不撤销；提交前进程结束则恢复上次成功值。

## 存储和备份边界

`AppearanceViewModel` 在 Activity 范围管理预览，`DataStoreSettingsRepository` 只发已保存值，以字段级读改写合并；新输入只取消防抖，不取消已开始的写入。

首次读取最多等待 1.5 秒，不阻塞主线程。超时或失败使用默认主题，禁用编辑并提供重试；不把损坏文件当作首次安装。保存失败回滚到上次成功值，并丢弃未保存预览。

`files/datastore/appearance.preferences_pb` 保存明暗、颜色来源、预设 ID、自定义历史色相及 `navigation_auto_hide`，不保存整套色板。同一文件还保存对比的 `compare_first_model_id`／`compare_second_model_id`，不缓存详情、评测或价格；恢复见[双模型对比](architecture.md#双模型对比的数据与状态)。旧式备份、云备份和设备迁移均排除此文件；Debug／Release 沙盒独立，与 [AI 配置](chat.md#配置存储)不共用存储。
