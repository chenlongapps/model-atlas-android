# 架构说明

本文只维护分层、数据流、状态与导航边界。字段见[页面字段](page-information.md)，视觉见[设计规范](Design.md)，远程／本地格式分别见 [Atlas 契约](atlas-data.md)与[本地数据](local-data.md)，测试入口见[分级测试](testing.md)。

## 代码入口与职责

项目只有 `:app`，以下路径相对于 [`app/src/main/java/io/github/chenlongapps/modelatlas/`](../app/src/main/java/io/github/chenlongapps/modelatlas/)。

| 入口 | 职责 |
| --- | --- |
| `ModelAtlasApplication.kt`、`MainActivity.kt` | Hilt 初始化；外观首读、全局主题、edge-to-edge 与系统栏同步 |
| `ui/ModelAtlasApp.kt` | 顶层 Pager、子页面、状态保存、共享转场宿主与底栏避让 |
| `ui/navigation/` | 玻璃底栏、按实际栏数／RTL 的命中几何、非消费式滚动显隐 |
| `data/remote/atlas/`、`data/remote/dto/` | Atlas 发布包及 Models.dev 服务商独立 DTO／解析，不包含 UI 或本地主键策略 |
| `data/mapper/`、`data/model/` | DTO → 本地类型；数据库投影 → 独立应用模型及纯业务规则 |
| `data/local/` | Room3 实体、JSON 值对象、DAO、事务投影与 `CatalogStore` |
| `data/repository/`、`di/DataModule.kt` | 请求协调、完整快照比较／提交、Flow、网络／数据库／时钟／IO 绑定 |
| `ui/modellist/`、`ui/favorites/` | 目录、查询、状态提醒／骨架、本地收藏及详情收藏操作 |
| `ui/modeldetail/`、`ui/modelcompare/`、`ui/benchmark/` | 详情、双模型对比、评测榜单及各自本地观察状态 |
| `ui/model/` | 共用身份展示、整卡共享转场与预测性返回控制器 |
| `ui/transition/` | 模型／评测共用的保留可见性与可寻址双端转场时间轴 |
| `ui/settings/`、`ui/theme/` | 设置子页、外观状态与语义主题；持久化见[外观设置](appearance-settings.md) |

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

- 每个新建的列表 ViewModel 先确保随包导入完成，再读取本地展示快照；任意目录记录或已提交发布标记存在即结束加载，即使 LLM 收录结果或正式发布为空。已提交的空发布不重复导入，也能离线显示空目录。确实无本地数据时联网提交，再等首个目录查询结果一起发布 `models` 和加载结束，避免短暂空态。
- 启动时展示缓存并自动联网同步一次；模型新增、删除、参数、价格、成绩、中文等变化全部静默写库，不保留待确认候选，不显示更新提示。内容比较忽略本地抓取时间与字典顺序，相同内容不写库。
- `ModelListViewModel` 只在启动、主动刷新成功或 Debug 显式用户操作后读取 `observeModels().first()`，不持续订阅目录。后台写库不改变本次列表内容、数量、顺序、查询匹配／选项或价格；切页、详情返回和同 ViewModel 的配置重建不发布新快照。下次新建列表 ViewModel 使用最新缓存，不把大快照写入 SavedState。
- 下拉刷新主动联网并在提交、重新读取成功后原子替换展示快照；进行中的启动请求可复用，重复下拉防重入。结果按数据库变化或展示快照变化显示“已更新”，两者均无变化显示“已是最新”，避免后台已写库后主动刷新误报未应用。查询不重置，UI 不主动回顶、不清展开状态；内容删除可能引起正常位置约束。
- 所有来源成功才形成完整候选；失败／取消不写半份。主动刷新读取失败仍保留旧展示快照、允许重试，取消不是错误。
- `observeModels()` 在仓库 `combine` 规范模型投影和报价投影，按规范 ID 去重；DAO 不选择默认报价，也不混入服务参数。详情／榜单另用事务 Relation，不拼接不同发布的独立成绩 Flow。
- 后台失败保留数据且不打扰浏览；主动失败非阻断。无本地数据的错误可重试；无目录与查询无匹配是不同状态。不轮询、不引入退出后的后台任务；网络约束见[数据源说明](data-source.md#请求与解析)。

### 查询与反馈

- `ModelListQuery` 是纯匹配条件，`ModelListViewModel.setQuery()` 在完整 `models` 上派生 `visibleModels`，不联网。搜索去首尾空白、按词不区分大小写，每个词须命中名称／ID／LAB 之一；不同筛选字段取交集、LAB 多选并集、每个方向要求全部所选模态。
- `ModalityCategory` 供列表／详情映射及查询／草稿恢复共用，原 MIME 保存在库中；已有缓存无需刷新即可按类别展示。转换细则见 [Atlas 模态](atlas-data.md#模态格式与展示类别)。
- `SavedStateHandle` 只存查询 JSON；`ModelListQueryJson`／`restoreModelListQuery()` 兼容删除的门槛／报价字段，保留其余有效条件，损坏回退。面板草稿在 UI 单独保存，只在“应用”时提交，离页关闭并清焦点。
- 候选选项来自当前完整展示快照和已选值，不随匹配结果缩小；`LabFilterOrder` 固定前八位 `openai → anthropic → google → deepseek → alibaba → meta → xai → moonshotai`，其余按展示目录计数降序、ID 兜底，已选失效值仍可取消。这不是热度或能力排名。
- `CatalogHeader` 和状态动画放在同一稳定列表项；`AnimatedCatalogStatusNotice` 只接收轻量 `CatalogNotice`，仅展示主动刷新结果／错误，无待确认入口。成功计时放在列表外，回收不重置，替换时取消旧计时。表现与优先级见[设计规范](Design.md#状态与反馈)。
- 骨架只用于首次无数据，复用真实查询控件测量，不创建假模型；共享一个绘制阶段时钟，非前台／离屏／禁用动画时静态，加载完成无额外延时。

## 顶层分页与子页面

- Release 为“模型｜评测｜对比｜设置”，Debug 追加调试；同一 `GlassNavigationItem` 列表决定 pageCount、key、内容与导航几何，按条目标识分发。顶层页面持续组合，普通切页不重置滚动、查询、提醒或展开状态。
- 详情、完整排行榜、外观、来源许可和 Debug 数据库管理是独立子页，隐藏底栏并保留但不放置主 Pager，防绘制、触摸、焦点和读屏穿透。不引入通用导航栈；根 Back 放行，弹层优先处理返回。
- 底栏实测高度保存为 dp，恢复首帧沿用，页面末尾增加实测高度＋32dp 留白；不因底栏显隐改变留白、夹回阅读位置。玻璃采样由 `layerBackdrop` 提供，当前支持设备为 API 33+，设计预览使用实色兜底。
- `NavigationScrollConnection` 始终零消费，只观察当前停稳页 `UserInput` 已消费纵向位移；松手／反向重置累计，不响应惯性、程序或独立弹层滚动。横向零位移 fling 不当成切页。阈值、动效、触摸探索规则见[设计规范](Design.md#悬浮底部导航)。
- 模型标签双击走 UI 的 `animateScrollToItem(0, 0)`，不经过 ViewModel 或刷新。监听不消费原点击，拖动／多指／长按／越界／其他标签中断双击序列，另提供等效无障碍操作。
- Debug 统计仅在切入停稳、重读或刷新结束后读取，离屏预组合不读库。手动刷新复用同一仓库；成功后 `onCatalogRefreshedExternally()` 等既有操作结束，仅重读本地展示快照并清过期反馈。数据库显式修改用 `onCatalogMutatedExternally()` 同样发布本地快照，均不二次联网、不恢复持续目录观察。

## 收藏的数据与状态

- `FavoritesViewModel` 只观察收藏／目录事务投影，`ModelListRoute` 收集；`CatalogFavoriteSnapshot` 将身份、参数与可用性投影到本次列表展示快照，避免后台目录恢复／删除模型让收藏卡片跳动；用户收藏增删仍实时生效。`CatalogEntries` 先匹配查询再将收藏稳定前置，可用组沿发布时间顺序，不可用收藏只留历史身份。目录计数不含不可用记录；其历史名称／ID／LAB 可搜索，其他未知筛选不匹配。读取失败保留普通目录并本地重试。
- `FavoriteDetailViewModel` 只观察当前模型；未读、读取失败或保存中禁用操作。添加在事务校验最新目录，保存后由 Flow 更新，不用乐观状态伪装成功；收藏成功停留，取消保存成功才返回，失败不返回。
- 详情打开期间固定来源条目位置，不冻结全列表；控制器、两端 Transition 及共享覆盖层交接后才释放重排。取消不可用收藏在写入前暂留来源，数据库先删除，交接后淡出；失败释放暂留。表与数据隔离见[本地收藏](local-data.md#本地收藏)。

## 评测总览与完整榜单

- `CatalogDao.observeBenchmarkRankings()` 同事务批量读定义／成绩／当前模型最小身份，`RoomBenchmarkRepository` 在 IO 映射，不逐模型查详情。只在停稳且生命周期可见时观察；完整榜单打开期间保留同一订阅，离屏取消，不新增联网。
- 同一 ViewModel 切页或前后台恢复观察时，保留上次成功结果（包括空榜单）和正文阅读位置，首个本地结果到达后直接替换，不先清空为 loading。仅首次读取／错误后重试显示 loading；真实读取失败仍清旧成绩，取消不当作失败，不将大快照写入 SavedState。
- 明确匹配定义且当前仍为 LLM 才参选；按实际版本／指标／单位隔离，兼容版本也不跨实际版本取优。未匹配不按名称归并，下架不借历史身份。
- 只对高分更优且有限的数值成绩，每模型取最高未舍入值；同模型同分按成绩 ID 选择配置，模型同分并列、后续跳号，再按名称／模型 ID 稳定排列。一位小数仅显示，不表示同配置公平比较。
- 文本、未知方向及低分更优成绩不生成排名、不在榜单展示，详情仍保留全部配置。`BenchmarkBoardMapper` 不改底层数据。
- `BenchmarkChart.kt` 用 BigDecimal 增留白并按 `1／2／5 × 10ⁿ` 向外取整；仅尊重明确且不排除实值的定义边界。Top 3 用自身集合、全榜用全部排名，滚动不改轴，单值／同分／零值仍有非零区间。机构色按背景提高至至少 3∶1，不按名次换色。
- 外壳仅保存榜单 ID；`SaveableStateHolder` 按 ID 隔离阅读状态，加载／错误另用列表状态。读取失败清旧成绩、本地重试；说明弹窗以榜单 ID 保存打开状态，旧描述不泄漏到错误／移除状态。页面字段与格式见[页面字段](page-information.md#评测页与完整排行榜)。

### 评测卡片与排行榜共享转场

- `ui/benchmark/BenchmarkSharedTransition.kt` 用 `BenchmarkContainerKey(boardId)` 匹配总览整卡与全屏榜单，用 `BenchmarkElementKey(boardId, role)` 匹配 Logo／名称；榜单 ID 区分实际版本／指标／单位，不以共用的评测定义 ID 或显示名匹配，也不与模型共享键混用。
- 两端共用 450ms 时间轴，容器圆角／底色连续，正文等比缩放并淡化，背景总览不整页淡出。Logo 两端底座比例不同，采用等比 `sharedBounds` 交接，宽字标不拉伸；名称共享边界仅包裹实际文字，不包含总览 `weight` 列留白，允许总览附版本、全榜仅名称之间淡化。非共享返回栏／正文在身份接近终点后交接，避免标题叠压。
- Top 3 成绩用独立的 `BenchmarkResultKey(boardId, modelId, role)` 匹配模型名／分数／轨道／填充，不按同名、并列名次或会变化的成绩记录 ID 配对。文字边界仅包裹实际内容，放松列最小宽度后仍以本征宽度约束真实段落，避免复用满列段落而超出文字布局；按 `Fit` 等比缩放及原有样式／单位淡化交接，分数不做计数动画。轨道／填充是两份独立 `sharedElement`，按各自边界重测宽高并保持圆角，层级高于容器且填充高于轨道，不叠加父级正文淡化。两页仍用各自固定轴，填充长度在两个真实端点之间插值，不修改分数、排名或计算规则。
- 全榜仅为当前前三个模型注册成绩匹配，滚动后停用成绩匹配，不为共享元素额外组合离屏条目或强制回顶。名次、全榜单项卡片背景及第四名之后内容仍随非共享正文淡入；没有匹配的内容走既有局部淡化，不补旧成绩或动画快照。
- 外壳保留主 Pager 的组合与两端转场布局，隐藏／退出端立即屏蔽触摸、焦点及读屏；共享层交接结束后才清理 `retainedBenchmarkId`。本地订阅持续至交接完成，不因按钮或系统返回重订阅。
- 简介不在列表初始位置时停用身份匹配，但仍缩回来源整卡，不强制回顶；来源不存在退化淡出。读取失败／榜单移除不保留旧身份或成绩，也不为动画缓存业务快照。
- 预测性返回、取消及入场中反向复用 `ui/transition/RetainedPageTransitionController.kt`，模型端保留类型别名；同一返回 handler 按当前子页分发进度／取消／确认，说明弹窗仍优先消费返回。配置恢复只恢复稳定全榜及阅读状态，不保存手势中间状态。

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
