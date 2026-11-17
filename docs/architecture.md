# 架构说明

本文只维护分层、数据流、状态与导航边界。字段见[页面字段](page-information.md)，视觉见[设计规范](Design.md)，远程／本地格式分别见 [Atlas 契约](atlas-data.md)与[本地数据](local-data.md)，测试入口见[分级测试](testing.md)。

## 代码入口与职责

项目只有 `:app`，以下路径相对于 [`app/src/main/java/io/github/chenlongapps/modelatlas/`](../app/src/main/java/io/github/chenlongapps/modelatlas/)。

| 入口 | 职责 |
| --- | --- |
| `ModelAtlasApplication.kt`、`MainActivity.kt` | Hilt 初始化；外观首读、全局主题、edge-to-edge 与系统栏同步 |
| `ui/ModelAtlasApp.kt` | 顶层 Pager、子页面、状态保存、共享转场宿主与底栏避让 |
| `ui/navigation/` | 主界面／助手 Navigation 3 路由、玻璃底栏、按实际栏数／RTL 的命中几何、非消费式滚动显隐 |
| `data/remote/atlas/`、`data/remote/dto/` | Atlas 发布包及 Models.dev 服务商独立 DTO／解析，不包含 UI 或本地主键策略 |
| `ai/`、`ai/openai/`、`ai/di/` | 独立 [LLM 请求层](llm-api.md)与 Hilt 绑定，不参与目录同步；`ai/config/` 管[配置存储](ai-configuration.md)，客户端不读存储 |
| `data/mapper/`、`data/model/` | DTO → 本地类型；数据库投影 → 独立应用模型及纯业务规则 |
| `data/local/` | Room3 实体、JSON 值对象、DAO、事务投影与 `CatalogStore` |
| `data/repository/`、`di/DataModule.kt` | 请求协调、完整快照比较／提交、Flow、网络／数据库／时钟／IO 绑定 |
| `ui/modellist/`、`ui/favorites/` | 目录、查询、状态提醒／骨架、本地收藏及详情收藏操作 |
| `ui/modeldetail/`、`ui/modelcompare/`、`ui/benchmark/` | 详情、双模型对比、评测榜单及各自本地观察状态 |
| `ui/model/` | 共用身份展示、整卡共享转场与预测性返回控制器 |
| `ui/transition/` | 模型／评测共用的保留可见性与可寻址双端转场时间轴 |
| `ui/settings/`、`ui/theme/` | 设置子页、外观状态与语义主题；持久化见[外观设置](appearance-settings.md) |
| `ui/chat/` | 独立 AI 文本会话、配置恢复／提交与显式请求生命周期；直接复用 `LlmClient`，对话仅内存，见[聊天](chat.md) |

Debug 工具位于 `src/debug/java/.../ui/debug/`，Release 同路径仅保留空 `DebugFeature`，不通过 `BuildConfig.DEBUG` 决定是否打包。隔离与签名见[双版本说明](debug-release-coexistence.md)。

### 分层约束

- DTO、本地 Entity／JSON、应用模型互不混用；UI 不接收 Entity、DTO 或原始请求配置。
- Route 获取 `hiltViewModel()`，生命周期感知收集；`Screen(uiState, callbacks)` 不依赖注入，可独立预览与测试。
- Repository 协调数据，不触发导航；UI 保存阅读、展开与弹层状态，ViewModel 保存业务选择／查询，不保存整份目录到 SavedState。
- 列表、详情、对比均复用 `LlmInclusionPolicy`；规范模型和服务侧字段不相互补齐，规则见[数据源说明](data-source.md)。

## 数据与状态流转

```text
首次无目录且无发布标记 → 导入 assets/atlas/ 真数据（无价格）
Room 本地目录 → ModelListViewModel → Route → Screen

refresh()（启动自动同步／用户主动刷新共用）
  → 并行读取 Atlas 整包 + Models.dev api.json?type=all
  → 原字节／结构／许可校验 + AtlasCatalogMapper
  → 比较完整候选快照
  → 模型增删及内容变化均直接提交；相同内容不写库
  → CatalogDao.replaceCatalog() 原子更新目录／成绩／定义／发布及可用收藏身份
  → 模型左连接 Flow + 独立报价投影 Flow
  → ModelListMapper / selectPriceFor → ModelSummary
  → 启动读取／主动刷新成功时发布列表展示快照 → models + query → visibleModels
```

### 缓存与列表展示快照

- 新列表 ViewModel 先确保随包导入，再读取本地快照。任意目录记录或发布标记存在即可结束加载，即使收录结果或发布为空；空发布不重复导入。确无本地数据时，联网提交并等首个查询结果后一起发布 `models` 和加载结束，避免闪现空态。
- 启动展示缓存并自动同步一次；模型增删、参数、价格、成绩和中文变化均静默写库，无待确认候选或更新提示。内容比较忽略抓取时间与字典顺序，相同不写库。
- `ModelListViewModel` 仅在启动、主动刷新成功或 Debug 显式操作后取 `observeModels().first()`，不持续订阅。后台写库不改当前列表的内容、数量、顺序、匹配／选项或价格；切页、详情返回、同 ViewModel 配置重建不发布新快照。新 ViewModel 读最新缓存，大快照不入 SavedState。
- 下拉在联网提交并重读成功后原子替换展示快照，防重复下拉，可复用启动请求。数据库或展示快照任一变化报“已更新”，均无变化才报“已是最新”。保留查询、展开与位置，不主动回顶；删除引起的正常位置约束除外。

### 刷新协调与失败

- `DefaultModelRepository.refresh()` 为启动／模型与评测主动刷新／Debug 并发调用共享同一在途结果，不靠互斥排队重复请求。完成后的新调用重新联网。
- 等待者取消不影响请求；发起者取消通知所有等待者，不创建脱离调用方的任务。随包导入与提交共用互斥保护。
- 全部来源成功才形成完整候选；失败／取消不写半份。后台失败不打扰浏览；主动联网／重读失败保留旧快照、非阻断且可重试，取消不报错。
- 无数据错误可重试，空目录与无匹配分开。不轮询、不加退出后后台任务；网络限制见[数据源说明](data-source.md#请求与解析)。

### 查询投影

`observeModels()` 在仓库 `combine` 规范模型与报价投影，按规范 ID 去重；DAO 不选默认价、不混入服务参数。详情／榜单用事务 Relation，不拼接不同发布的独立成绩 Flow。

### 查询与反馈

- `ModelListQuery` 是纯匹配条件，`ModelListViewModel.setQuery()` 在完整 `models` 上派生 `visibleModels`，不联网。搜索去首尾空白、按词不区分大小写，每个词须命中名称／ID／LAB 之一；不同筛选字段取交集、LAB 多选并集、每个方向要求全部所选模态。
- `ModalityCategory` 供列表／详情映射及查询／草稿恢复共用，原 MIME 保存在库中；已有缓存无需刷新即可按类别展示。转换细则见 [Atlas 模态](atlas-data.md#模态格式与展示类别)。
- `SavedStateHandle` 只存查询 JSON；`ModelListQueryJson`／`restoreModelListQuery()` 兼容删除的门槛／报价字段，保留其余有效条件，损坏回退。面板草稿在 UI 单独保存，只在“应用”时提交，离页关闭并清焦点。
- 候选选项来自当前完整展示快照和已选值，不随匹配结果缩小；`LabFilterOrder` 固定前八位 `openai → anthropic → google → deepseek → alibaba → meta → xai → moonshotai`，其余按展示目录计数降序、ID 兜底，已选失效值仍可取消。这不是热度或能力排名。
- `CatalogHeader` 与状态动画共用稳定列表项，`AnimatedCatalogStatusNotice` 仅收轻量 `CatalogNotice`；成功计时在列表外，生命周期与反馈见[设计规范](Design.md#状态与反馈)。
- 骨架复用真实查询控件测量，共享一个绘制阶段时钟；显示与动画条件见同节设计规范。

## 顶层分页与子页面

### 路由边界

| 层级 | 管理范围 |
| --- | --- |
| `AssistantNavigation` | `MainDestination`／`AssistantDestination` 两个可序列化 `NavKey`，由 `rememberNavBackStack`／`NavDisplay` 管理 |
| Main 内 Pager | 模型／评测／对比／设置，Debug 追加调试；模型默认首页，助手不可横滑进入 |
| Main 内既有子页 | 详情、完整榜单、外观、来源许可、Debug 数据库；不迁入 NavDisplay，不改共享转场控制器 |
| NavDisplay 外共享底栏 | 主路由导航胶囊＋AI 按钮；助手路由同一容器变输入栏＋发送／停止，见[设计规范](Design.md#ai-助手) |

AI push 防重入，胶囊条目与 Pager 索引一一对应。Main 根 Back 放行，弹层优先；助手返回和请求停止见[聊天](chat.md#离页与返回)。

### 保存、组合与可见性

- Main 用 Navigation 3 默认装饰器保存分页／列表／展开，普通切页持续组合。进入助手立即关闭 Main 交互、读屏和活动信号，过渡后移出组合；返回恢复来源，不重建 Activity 级 ViewModel、不启动目录刷新。
- 预测性返回可能预组合非活动来源页；滚动连接的 remember key 须含路由活动状态，确认后重新启用显隐监听。
- 助手用宿主级 `SaveableStateHolder` 保存阅读／跟随，pop 后保留供重入；对话、草稿、配置不进入保存状态。退出端绘制到过渡结束，但 pop 首帧起屏蔽焦点、读屏和全部触摸（含复制）。
- Main 子页保留但不放置主 Pager，隐藏底栏，防绘制／触摸／焦点／读屏穿透；共享转场期间两端放置例外见下文。

### 底栏测量与滚动

- 整组最大高度保存为 dp，恢复首帧沿用；避让、阈值、动效及触摸探索见[设计规范](Design.md#悬浮底部导航)。玻璃由 NavDisplay 的 `layerBackdrop` 采样，API 33+；预览用实色和静态 Main。
- `NavigationScrollConnection` 零消费，仅观察当前停稳页 `UserInput` 已消费纵向位移；松手／反向重置，不响应惯性、程序或弹层滚动。横向零位移 fling 不算切页。
- 自动隐藏复用现有[偏好链路](appearance-settings.md#底栏偏好)，切换时清显隐／累计状态，仍服从子页隐藏；不改阅读位置、留白或助手输入栏。
- 模型双击由 UI `animateScrollToItem(0, 0)` 执行，不经 ViewModel 或刷新。监听不消费原点击；拖动、多指、长按、越界、其他标签中断序列。

### Debug 显式操作

统计仅在切入停稳、重读或刷新结束后读库，离屏预组合不读。手动刷新复用仓库，`onCatalogRefreshedExternally()` 等既有操作结束后重读本地展示快照、清过期反馈；数据库修改用 `onCatalogMutatedExternally()` 发布快照。两者均不二次联网、不恢复持续观察。

## 收藏的数据与状态

- `FavoritesViewModel` 观察收藏／目录事务投影，`ModelListRoute` 收集。`CatalogFavoriteSnapshot` 绑定本次展示快照的身份、参数与可用性，后台目录变化不跳卡，用户增删实时生效。
- `CatalogEntries` 先匹配查询再稳定前置收藏，可用组按发布时间，不可用只留历史身份且不计目录总数；历史名称／ID／LAB 可搜索，未知筛选不匹配。读取失败保留普通目录、本地重试。
- `FavoriteDetailViewModel` 只观察当前模型；未读、读取失败或保存中禁用操作。添加在事务校验最新目录，保存后由 Flow 更新，不用乐观状态伪装成功；收藏成功停留，取消保存成功才返回，失败不返回。
- 详情打开期间固定来源条目位置，不冻结全列表；控制器、两端 Transition 及共享覆盖层交接后才释放重排。取消不可用收藏在写入前暂留来源，数据库先删除，交接后淡出；失败释放暂留。表与数据隔离见[本地收藏](local-data.md#本地收藏)。

## 评测总览与完整榜单

### 观察与刷新

- `CatalogDao.observeBenchmarkRankings()` 同事务批量读定义／成绩／当前模型最小身份，`RoomBenchmarkRepository` 在 IO 映射，不逐模型查详情。停稳且生命周期可见才观察，打开全榜保留订阅，离屏取消；恢复／本地重试不联网。
- 总览下拉／读屏“刷新评测”由 `BenchmarkViewModel` 调 `ModelRepository.refresh()`，复用完整目录事务；首次本地读取中或已在刷新时不重复请求，不建独立评测同步。
- 网络完成后取 `observeBoards().first()`，再结束指示器并报更新／最新；目录或相对下拉前的榜单任一变化算更新，合法空结果也可成功。写模型缓存但不替模型页发布快照。
- 主动请求保留榜单／阅读位置；联网或主动重读失败只加可关、可重试的非阻断提醒，取消不报错。请求属 ViewModel，切页后可完成，但不恢复离屏订阅。全榜无下拉，按钮／系统返回不重订阅。
- 同 ViewModel 恢复观察时保留上次成功值（含空）和阅读位置，首个本地值直接替换；仅首次／错误重试显示 loading。持续观察失败清旧成绩，与主动失败分开；大快照不入 SavedState。

### 排名、分榜与坐标

- 总览按定义 `popularity` 降序，缺失最后；同值或均缺失时按名称（忽略大小写）、实际版本、完整榜单 ID 稳定排序。同定义的实际版本各自继承热度，不改变榜单身份或榜内模型排名；不硬编码线上热度。
- 明确匹配定义且当前仍为 LLM 才参选；按指标／单位隔离。定义版本为 `unspecified` 时，不校验成绩版本并归入同一个 `unspecified` 榜单；其他定义按实际版本分榜，兼容版本也不跨实际版本取优。关联唯一性见 [Atlas 契约](atlas-data.md#成绩及本地化)；未匹配不按名称归并，下架不借历史身份。
- `RoomBenchmarkRepository`／`RoomCatalogStore` 在开始榜单／详情本地观察前，先事务修复旧 Atlas 缓存中受 `unspecified` 规则影响的派生关联；不联网、不改发布身份或成绩原始版本，不替模型列表发布新展示快照。修复范围见[本地数据](local-data.md#独立评测成绩)。
- 只对高分更优且有限的数值成绩，每模型取最高未舍入值；同模型同分按成绩 ID 选择配置，模型同分并列、后续跳号，再按名称／模型 ID 稳定排列。一位小数仅显示，不表示同配置公平比较。
- 文本、未知方向及低分更优成绩不生成排名、不在榜单展示，详情仍保留全部配置。`BenchmarkBoardMapper` 不改底层数据。
- `BenchmarkChart.kt` 用 BigDecimal 增留白并按 `1／2／5 × 10ⁿ` 向外取整；仅尊重明确且不排除实值的定义边界。Top 3 用自身集合、全榜用全部排名，滚动不改轴，单值／同分／零值仍有非零区间。颜色约束见[设计规范](Design.md#评测榜单)。

### 阅读与弹窗恢复

- 外壳仅存榜单 ID，`SaveableStateHolder` 按 ID 隔离阅读，加载／错误另用列表状态。重建／返回取消保留位置；确认退出且交接完成才清状态，重入从顶部恢复身份／前三名匹配，总览位置不动。
- 读取失败清旧成绩、本地重试；说明弹窗按榜单 ID 保存，错误／移除不留旧描述。字段与格式见[页面字段](page-information.md#评测页与完整排行榜)。

### 评测卡片与排行榜共享转场

实现：`ui/benchmark/BenchmarkSharedTransition.kt`。

| 对象 | 匹配键 | 边界 |
| --- | --- | --- |
| 整卡／全屏 | `BenchmarkContainerKey(boardId)` | boardId 含实际版本／指标／单位，不用定义 ID 或显示名，不混模型键 |
| Logo／名称 | `BenchmarkElementKey(boardId, role)` | Logo 等比 `sharedBounds`；名称仅实际文字，不含 weight 留白，允许两端版本文字淡化 |
| 前三名名称／分数／轨道／填充 | `BenchmarkResultKey(boardId, modelId, role)` | 不按同名、并列名次或可变成绩 ID 配对 |

- **时间轴**：两端 450ms，圆角／底色连续，正文等比缩放淡化，背景不整页淡出；返回栏／正文在身份接近终点后交接，避免叠标题。
- **文字**：解除列最小宽度后仍以本征宽度约束真实段落；`Fit` 等比缩放、样式／单位淡化，分数不计数动画。
- **柱条**：轨道／填充各自 `sharedElement`，重测边界、保持圆角；填充高于轨道、两者高于容器，不叠加父正文淡化。各页固定轴不变，只插值真实端点，不改分数／排名。
- **匹配范围**：全榜仅当前前三名注册成绩，滚动后停匹配，不组合离屏项或强制回顶；名次、单项背景、第四名以后走正文淡化，无匹配不补旧成绩／动画快照。
- **退出**：保留 Pager 组合及两端布局，退出立即屏蔽触摸／焦点／读屏；交接后清 `retainedBenchmarkId`，本地订阅持续至交接，不因按钮／系统返回重订阅。
- **降级**：简介离开初始位置则停身份匹配、仍缩回整卡；来源不存在淡出，错误／移除不留旧身份或成绩，不缓存业务快照。
- **返回**：复用 `RetainedPageTransitionController`，同一 handler 按子页分发进度／取消／确认，弹窗优先；配置只恢复稳定全榜／阅读，不保存手势。控制器路径及模型端细节见[预测性返回](#模型详情预测性返回)。

## 双模型对比的数据与状态

- `ModelCompareViewModel` 仅保存两个规范 ID，拒绝重复／目录外模型／非法槽位。停稳且可见才观察目录、收藏与所选详情，离屏取消，替换立刻取消旧模型订阅；重入读最新本地，任何重试不联网。
- 候选与默认价来自 `observeModels()`；参数／能力／许可来自 `observeModelDetail(id)`。收藏只保留 ID 集合用于候选稳定前置，不可用收藏不补入；读取失败回退目录顺序，不影响正文或选择。
- 目录与两侧模型各自处理加载／错误／移除；移除或读取异常清该侧旧内容，一侧失败不清另一侧。完整表格等默认价与两侧详情就绪，加载／失败使用独立列表状态，避免短内容夹回正文位置。
- UI 保存正文、档位、服务 ID 与许可展开状态，位于惰性条目外并随两模型 ID 组合重置；选择面板关闭不改变选择，选中候选立即提交。等高卡片与宽度分级见[设计规范](Design.md#双模型对比)。

## 模型详情的数据与导航

- `observeModelDetail(id)` 用单个 `@Transaction` Relation 读规范模型、全部显式关联服务／服务商、成绩及可选定义；含无价服务，不用列表单报价投影。IO 映射，重复值不重发；不存在／不收录返回 null，空服务仍为有效详情。
- `ModelDetailViewModel.open(id)` 首个结果前加载；错误提供本地重试，不联网／不应用候选。切换取消旧订阅，退出动画结束后关闭；移除不继续显示旧详情。
- `DetailLink.kt` 对保留的外链校验 HTTP(S)、主机及用户信息，打开失败提供反馈；不展示／执行服务请求 body 或 headers。界面精简不移除底层安全边界。
- 外壳只保存规范 ID，点击时临时捕获 `ModelIdentitySnapshot` 供加载头部，不构造假详情、不持久化、不等待数据库才跳转。真实结果原位接管不重启动画；错误／不存在清快照，恢复只按 ID 读取。
- 公共外层 `LazyColumn` 放身份和标签，尾项 `HorizontalPager` 仅放三个正文；上滑先滚公共区再滚正文，正文到顶下滑恢复公共区。各正文／标签状态在 Pager 外保存，加载／错误另用列表；切页不重置公共标题位置。
- 固定返回栏由大标题实测边界驱动品牌／模型名交接，不靠独立计时。两层始终测量，半程切换读屏可见性，不推正文；模型名紧凑层可省略但读屏完整。系统栏／键盘统一消费 insets，不重复避让。

### 共享容器与返回交接

- `ModelSharedTransition.kt` 以 `ModelContainerKey(modelId)` 匹配整卡／全屏容器，以 `ModelSharedElementKey(modelId, role)` 匹配 Logo／名称／机构／权重；不用 LAB 作 key，两类 key 不混用。
- 两端保留 `Transition<EnterExitState>` 与 `AnimatedVisibilityScope`；容器及身份同一 450ms 时间轴。根覆盖层身份为 2，卡片为 1，详情为 0；身份继承动态轮廓裁剪，不叠加父级缩放或内容淡化。正文 `scaleToBounds(FillWidth, TopStart)` 等比缩放并局部淡化，背景目录不整页淡出。
- 主 Pager 全程保留组合，动画时两页参与放置，退出页立即禁止操作／读屏。详情有纵向滚动后停身份匹配，但整页仍缩回来源；来源无匹配才退化淡出，不强制任一端回顶。
- `sharedBounds` 的 `ExitTransition.None` 不会自动隐藏保留的表面：只有详情 current／target 均为 `PostExit` 且扩展进度归零时，先停止绘制再清组合。不要等滞后的 `isRunning`，也不要只看状态相等提前隐藏尚在收缩的容器；不用固定 delay。

### 模型详情预测性返回

- `ModelDetailTransitionController` 是公共 `RetainedPageTransitionController` 的模型端类型别名，只管 UI，统一两套稳定公开 `SeekableTransitionState`，区分 Idle／Entering／BackDragging／BackCancelling／BackCommitting／Exiting；阶段／手势代次变化才取消旧任务，进度经 `snapshotFlow` 合并、两端串行 `seekTo()`。
- `NavigationBackHandler` 获取系统进度，取消／确认明确结算；Idle 不重置，重复退出忽略。拖动／暂停／100% 仍保留两端布局与选中 ID，并屏蔽底层交互；取消恢复标签／阅读状态，确认只完成剩余收缩。
- 入场中返回沿原时间轴倒放；取消途中再拖从实际 fraction 开始，帧时钟遵循 `MotionDurationScale`。文字及正文用同一 Transition 显式透明度，避免 `PostExit → Visible` 默认 spring 干扰精确寻址；非共享详情正文在退出最初 120ms 清出。
- Compose 1.12.1 的 `animateTo()` 端点 fraction 归零和共享层时长重建会重复应用旧 segment，因此以帧时钟推进、端点 `snapTo()` 清缓存；不用实验性 `createChildTransition()` 或视觉遮盖。
- 倒放到 current 端点前，先新方向 `seekTo(1)`，待两端 `SideEffect` 确认目标组合并经过下一布局帧，再取消旧追赶并 snap，防一帧 Logo 无人绘制；新手势先完成待归整交接，不等额外整段动画。
- 确认完成后清选中 ID，再等共享层交接清 `retainedModelId`、快照及订阅。配置恢复为稳定详情，不保存半途手势。dispatcher／像素测试不能替代系统端到端返回验收，见 [TODO](../TODO.md#待验收与发布)。
