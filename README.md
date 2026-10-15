# ModelAtlas Android

基于 Kotlin 和 Jetpack Compose 的 Android 应用，用于发现、了解和对比 AI 模型。

> 🚧 项目处于早期开发阶段，目前使用本地静态数据演示模型列表，尚未接入 Models.dev API。

## 已实现

- `MainActivity` 启动入口与 Hilt 依赖注入
- 跟随系统切换的 Material 3 浅色、深色主题
- 最小 MVVM 链路：静态仓库 → ViewModel → StateFlow → Compose 列表

## 功能规划

- 浏览、搜索和筛选 AI 模型
- 查看模型能力、上下文窗口及 API 定价
- 对比多个模型的参数与价格
- 收藏模型，支持本地缓存
- 后续扩展：价格追踪、新模型提醒、模型更新历史

## 技术栈

Kotlin · Jetpack Compose · Material 3 · MVVM · Hilt · Retrofit / OkHttp · Kotlinx Serialization · Room · Coroutines / Flow

## 数据来源

计划使用 [Models.dev](https://models.dev/) 的公开 API：[api.json](https://models.dev/api.json)，[models.json](https://models.dev/models.json)，可用字段以数据源为准。

本项目为独立第三方应用，与 Models.dev 及各 AI 模型提供商无官方隶属关系。

## 参与贡献

欢迎提交 Issue，反馈问题或分享建议。

---

作者：[chenlongapps](https://github.com/chenlongapps) · 许可证待定
