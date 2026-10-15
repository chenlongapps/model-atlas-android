# 架构说明

本文描述当前已实现的本地模型列表。搜索、详情、对比、收藏和远程数据接入仍属于规划，任务状态见 [TODO.md](../TODO.md)。

## 代码入口与职责

项目只有 `:app` 模块。下表中的 Kotlin 路径均相对于 [`app/src/main/java/io/github/chenlongapps/modelatlas/`](../app/src/main/java/io/github/chenlongapps/modelatlas/)。

| 路径 | 当前职责 |
| --- | --- |
| [`ModelAtlasApplication.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ModelAtlasApplication.kt) | 通过 `@HiltAndroidApp` 初始化 Hilt |
| [`MainActivity.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/MainActivity.kt) | 应用入口，启用边到边显示，承载 `ModelAtlasTheme` 与 `ModelListRoute` |
| [`data/model/AiModel.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/model/AiModel.kt) | 列表使用的数据模型，目前仅含 `id`、`name`、`provider`、`description` 四个非空 `String` 字段 |
| [`data/repository/ModelRepository.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/repository/ModelRepository.kt) | 可通过构造函数注入的具体仓库类，同步返回三个本地示例 |
| [`ui/modellist/ModelListViewModel.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListViewModel.kt) | 定义 `ModelListUiState`，读取仓库数据并暴露只读 `StateFlow` |
| [`ui/modellist/ModelListScreen.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListScreen.kt) | 包含 Route、列表界面、模型卡片及浅色／深色预览 |
| [`ui/theme/Theme.kt`](../app/src/main/java/io/github/chenlongapps/modelatlas/ui/theme/Theme.kt) | 定义 Compose 主题与颜色；Activity／窗口主题的修改位置见 [AGENTS.md](../AGENTS.md) |

## 数据与状态流转

```text
ModelRepository.getModels()（本地同步数据）
    → ModelListViewModel.uiState: StateFlow<ModelListUiState>
    → ModelListRoute（collectAsStateWithLifecycle）
    → ModelListScreen(uiState)
    → ModelCard(model)
```

- `ModelListViewModel` 在初始化时读取仓库；`ModelListUiState` 目前只包含 `models`，没有加载、错误或筛选状态，也没有异步刷新流程。
- `ModelListRoute` 负责获取 Hilt ViewModel 和生命周期感知的状态收集，不直接请求数据源。
- `ModelListScreen` 通过参数接收状态，不依赖 Hilt 或 ViewModel。修改时保留这一边界，使预览和后续界面测试可以使用独立数据。
- 列表使用模型 `id` 作为条目 key，因此数据源需要提供唯一且稳定的标识；当前示例 ID 不代表已确认的远程 ID 规则。

## 当前实现边界

- 列表仅展示名称、提供商和描述，页面注明使用本地示例数据。
- Retrofit／OkHttp、Kotlinx Serialization 和 Room3 已引入，但没有 API 客户端、远程数据映射、数据库或缓存实现。
- [AndroidManifest.xml](../app/src/main/AndroidManifest.xml) 尚未声明 `INTERNET` 权限。
- 当前没有模型详情或多页面导航流程。新增功能前先确认任务范围，不因依赖已存在就假定对应能力已实现。
- Models.dev 接入前的核实要求见 [数据源说明](data-source.md)。

## 现有业务单元测试

测试源码位于 [`app/src/test/java/io/github/chenlongapps/modelatlas/`](../app/src/test/java/io/github/chenlongapps/modelatlas/)。

| 测试 | 已覆盖内容 |
| --- | --- |
| [`ModelRepositoryTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/data/repository/ModelRepositoryTest.kt) | 返回三个示例、ID 唯一、展示字段非空白 |
| [`ModelListViewModelTest`](../app/src/test/java/io/github/chenlongapps/modelatlas/ui/modellist/ModelListViewModelTest.kt) | 初始状态与仓库数据一致、不对外暴露 `MutableStateFlow` |

这些测试不代表已验证网络、持久化或业务 UI 交互。构建与测试命令统一见 [AGENTS.md](../AGENTS.md)。
