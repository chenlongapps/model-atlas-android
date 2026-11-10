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

checkForUpdates() / refresh()
  → 并行读取 Atlas 整包 + Models.dev api.json?type=all
  → 原字节／结构／许可校验 + AtlasCatalogMapper
  → 比较完整候选快照
  → 后台检查：模型 ID 增删 → 暂存 → 用户确认 applyPendingUpdate()
              ID 不变但内容变 → 静默提交；相同内容不写库
  → 主动刷新：直接提交变化
  → CatalogDao.replaceCatalog() 原子更新目录／成绩／定义／发布及可用收藏身份
  → 模型左连接 Flow + 独立报价投影 Flow
  → ModelListMapper / selectPriceFor → ModelSummary → models + query → visibleModels
```

- 首次有任意目录记录即本地展示，即使 LLM 收录结果为空也不当成未加载；已提交的空发布不重复导入。首次加载／重试等提交后的首个目录查询结果再一起发布 `models` 和加载结束，避免短暂空态。
- 后台只比较完整规范模型 ID 集合决定是否提示，不受搜索、LLM 收录或服务变动影响；内容比较忽略本地抓取时间与字典顺序。候选仅存在仓库内存，进程重启重新检查。
- 所有来源成功才形成完整候选；失败／取消不写半份。`checkForUpdates()` 区分无变化、静默更新和待确认；确认提交已有快照不重下，检查期间下拉复用请求。初始化／检查／刷新／提交防重入，取消不是错误。
- `observeModels()` 在仓库 `combine` 规范模型投影和报价投影，按规范 ID 去重；DAO 不选择默认报价，也不混入服务参数。详情／榜单另用事务 Relation，不拼接不同发布的独立成绩 Flow。
- 后台失败保留数据且不打扰浏览；主动失败非阻断。无本地数据的错误可重试；无目录与查询无匹配是不同状态。不轮询、不引入退出后的后台任务；网络约束见[数据源说明](data-source.md#请求与解析)。

### 查询与反馈

- `ModelListQuery` 是纯匹配条件，`ModelListViewModel.setQuery()` 在完整 `models` 上派生 `visibleModels`，不联网。搜索去首尾空白、按词不区分大小写，每个词须命中名称／ID／LAB 之一；不同筛选字段取交集、LAB 多选并集、每个方向要求全部所选模态。
- `ModalityCategory` 供列表／详情映射及查询／草稿恢复共用，原 MIME 保存在库中；已有缓存无需刷新即可按类别展示。转换细则见 [Atlas 模态](atlas-data.md#模态格式与展示类别)。
- `SavedStateHandle` 只存查询 JSON；`ModelListQueryJson`／`restoreModelListQuery()` 兼容删除的门槛／报价字段，保留其余有效条件，损坏回退。面板草稿在 UI 单独保存，只在“应用”时提交，离页关闭并清焦点。
- 候选选项来自完整目录和已选值，不随匹配结果缩小；`LabFilterOrder` 固定前八位 `openai → anthropic → google → deepseek → alibaba → meta → xai → moonshotai`，其余按完整目录计数降序、ID 兜底，已选失效值仍可取消。这不是热度或能力排名。
- `CatalogHeader` 和状态动画放在同一稳定列表项；`AnimatedCatalogStatusNotice` 只接收轻量 `CatalogNotice`，不以目录变化重播动画。成功计时放在列表外，回收不重置，替换时取消旧计时；关闭错误不丢待更新快照。表现与优先级见[设计规范](Design.md#状态与反馈)。
- 骨架只用于首次无数据，复用真实查询控件测量，不创建假模型；共享一个绘制阶段时钟，非前台／离屏／禁用动画时静态，加载完成无额外延时。

## 顶层分页与子页面

- Release 为“模型｜评测｜对比｜设置”，Debug 追加调试；同一 `GlassNavigationItem` 列表决定 pageCount、key、内容与导航几何，按条目标识分发。顶层页面持续组合，普通切页不重置滚动、查询、提醒或展开状态。
- 详情、完整排行榜、外观、来源许可和 Debug 数据库管理是独立子页，隐藏底栏并保留但不放置主 Pager，防绘制、触摸、焦点和读屏穿透。不引入通用导航栈；根 Back 放行，弹层优先处理返回。
- 底栏实测高度保存为 dp，恢复首帧沿用，页面末尾增加实测高度＋32dp 留白；不因底栏显隐改变留白、夹回阅读位置。玻璃采样由 `layerBackdrop` 提供，当前支持设备为 API 33+，设计预览使用实色兜底。
- `NavigationScrollConnection` 始终零消费，只观察当前停稳页 `UserInput` 已消费纵向位移；松手／反向重置累计，不响应惯性、程序或独立弹层滚动。横向零位移 fling 不当成切页。阈值、动效、触摸探索规则见[设计规范](Design.md#悬浮底部导航)。
- 模型标签双击走 UI 的 `animateScrollToItem(0, 0)`，不经过 ViewModel 或刷新。监听不消费原点击，拖动／多指／长按／越界／其他标签中断双击序列，另提供等效无障碍操作。
- Debug 统计仅在切入停稳、重读或刷新结束后读取，离屏预组合不读库。手动刷新复用同一仓库；成功后 `onCatalogRefreshedExternally()` 等既有操作结束，仅恢复本地观察并清过期提示。数据库修改用 `onCatalogMutatedExternally()`，保留待更新提示，均不二次联网。

## 收藏的数据与状态

- `FavoritesViewModel` 只观察收藏／目录事务投影，`ModelListRoute` 收集；`CatalogEntries` 先匹配查询再将收藏稳定前置，可用组沿发布时间顺序，不可用收藏只留历史身份。目录计数不含不可用记录；其历史名称／ID／LAB 可搜索，其他未知筛选不匹配。读取失败保留普通目录并本地重试。
- `FavoriteDetailViewModel` 只观察当前模型；未读、读取失败或保存中禁用操作。添加在事务校验最新目录，保存后由 Flow 更新，不用乐观状态伪装成功；收藏成功停留，取消保存成功才返回，失败不返回。
- 详情打开期间固定来源条目位置，不冻结全列表；控制器、两端 Transition 及共享覆盖层交接后才释放重排。取消不可用收藏在写入前暂留来源，数据库先删除，交接后淡出；失败释放暂留。表与数据隔离见[本地收藏](local-data.md#本地收藏)。

## 评测总览与完整榜单

- `CatalogDao.observeBenchmarkRankings()` 同事务批量读定义／成绩／当前模型最小身份，`RoomBenchmarkRepository` 在 IO 映射，不逐模型查详情。只在停稳且生命周期可见时观察；完整榜单打开期间保留同一订阅，离屏取消，不新增联网。
- 明确匹配定义且当前仍为 LLM 才参选；按实际版本／指标／单位隔离，兼容版本也不跨实际版本取优。未匹配不按名称归并，下架不借历史身份。
- 只对高分更优且有限的数值成绩，每模型取最高未舍入值；同模型同分按成绩 ID 选择配置，模型同分并列、后续跳号，再按名称／模型 ID 稳定排列。一位小数仅显示，不表示同配置公平比较。
- 文本、未知方向及低分更优成绩不生成排名、不在榜单展示，详情仍保留全部配置。`BenchmarkBoardMapper` 不改底层数据。
- `BenchmarkChart.kt` 用 BigDecimal 增留白并按 `1／2／5 × 10ⁿ` 向外取整；仅尊重明确且不排除实值的定义边界。Top 3 用自身集合、全榜用全部排名，滚动不改轴，单值／同分／零值仍有非零区间。机构色按背景提高至至少 3∶1，不按名次换色。
- 外壳仅保存榜单 ID；`SaveableStateHolder` 按 ID 隔离阅读状态，加载／错误另用列表状态。读取失败清旧成绩、本地重试；说明弹窗以榜单 ID 保存打开状态，旧描述不泄漏到错误／移除状态。页面字段与格式见[页面字段](page-information.md#评测页与完整排行榜)。

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

- `ModelDetailTransitionController` 只管 UI，统一两套稳定公开 `SeekableTransitionState`，区分 Idle／Entering／BackDragging／BackCancelling／BackCommitting／Exiting；阶段／手势代次变化才取消旧任务，进度经 `snapshotFlow` 合并、两端串行 `seekTo()`。
- `NavigationBackHandler` 获取系统进度，取消／确认明确结算；Idle 不重置，重复退出忽略。拖动／暂停／100% 仍保留两端布局与选中 ID，并屏蔽底层交互；取消恢复标签／阅读状态，确认只完成剩余收缩。
- 入场中返回沿原时间轴倒放；取消途中再拖从实际 fraction 开始，帧时钟遵循 `MotionDurationScale`。文字及正文用同一 Transition 显式透明度，避免 `PostExit → Visible` 默认 spring 干扰精确寻址；非共享详情正文在退出最初 120ms 清出。
- Compose 1.12.1 的 `animateTo()` 端点 fraction 归零和共享层时长重建会重复应用旧 segment，因此以帧时钟推进、端点 `snapTo()` 清缓存；不用实验性 `createChildTransition()` 或视觉遮盖。
- 倒放到 current 端点前，先新方向 `seekTo(1)`，待两端 `SideEffect` 确认目标组合并经过下一布局帧，再取消旧追赶并 snap，防一帧 Logo 无人绘制；新手势先完成待归整交接，不等额外整段动画。
- 确认完成后清选中 ID，再等共享层交接清 `retainedModelId`、快照及订阅。配置恢复为稳定详情，不保存半途手势。dispatcher／像素测试不能替代系统端到端返回验收，见 [TODO](../TODO.md#待验收与发布)。
