# 本地目录数据

本文维护 `catalog.db` 的存储契约；远程 DTO 与发布校验见 [Atlas](atlas-data.md)／[Models.dev](data-source.md)，观察与 UI 状态见[架构](architecture.md)。

## 表与开发基线

`CatalogDatabase` 使用 Room3／`AndroidSQLiteDriver`，由 Hilt 提供同一连接。仍是未发布的 **Schema v1 开发基线**，导出到 `app/schemas/`；新增列／表直接更新基线，不自动迁移旧开发库、不启用破坏性重建或清数据。旧安装可能无法打开，需开发者自行处理，不能据此自动删除收藏。

源码相对于 [`data/local/`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/)：

| 表（Entity） | 主键 | 职责 |
| --- | --- | --- |
| `models`（`ModelEntity`） | `id` | 规范模型元数据／能力／限制／参数量／双语描述／权重，不含服务报价或内嵌成绩 |
| `providers`（`ProviderEntity`） | `id` | 服务商身份、文档／SDK／环境变量名称，不嵌套服务列表 |
| `model_offerings`（`ModelOfferingEntity`） | `(providerId, id)` | 服务侧能力／限制／状态、完整报价／模式／请求覆盖 |
| `favorites`（`FavoriteEntity`） | `modelId` | 本地用户收藏、时间与最后可用身份，独立于远端快照 |
| `benchmark_scores`（`BenchmarkScoreEntity`） | `id` | 每条模型评测配置的数值或文本原分数与元数据 |
| `benchmark_definitions`（`BenchmarkDefinitionEntity`） | `id` | 名称／版本／指标、双语说明、兼容版本、单位／方向／边界及可空 REAL 热度 `popularity` 等定义 |
| `catalog_release`（`CatalogReleaseEntity`） | `id = 1` | 内容哈希、git 提交、发布时间，不可排序的发布身份 |
| `benchmark_version`（`BenchmarkVersionEntity`） | `id = 1` | 旧整数版本兼容测试，不参与生产更新判断 |

“三表”指目录模型／服务商／服务，“五表”指 Debug 开放编辑的原业务表，不代表数据库总表数。

## 标识、关联与来源

- 模型 `id` 为 Atlas `models.json` 路径键，无内嵌 ID；其 `remoteId` 明确取路径键。服务商 ID 为 `api.json` 顶层键，服务 ID 为该服务商模型字典键，`remoteId` 另留 DTO 条目内 ID，不假定两者相同。
- 服务复合主键只在服务商内唯一，不使用自增或拼接；`providerId` 外键级联服务，复合索引已以前缀覆盖，不再建重复单列索引。
- `canonicalModelId` 仅保留显式关联、可空、普通索引但无模型外键：来源暂不同步或模型缺失仍可保存服务，不以模糊匹配修正。
- 当前模型 `source = model-atlas-data`，服务商／服务为 `models.dev`；来源字段不代表支持任意多来源共用 ID。
- `fetchedAtEpochMillis` 必须由调用方显式给 Unix 毫秒，同一响应保持一致；`sourceLastUpdated` 保留原字符串，不等同抓取时间或价格生效时间。
- 能力 Boolean?，限制 Long?，日期／类型／状态／模态保留开放字符串；缺失、false、零、空集合各自保存。LAB 是 UI 的 ID 前缀标签，不新增已核实开发商关联。

## 快照事务与查询

- `CatalogSnapshot` 包含三目录表，正式 Atlas 发布须同时含 `scores`／`definitions`／`release`。`readCatalog()` 同事务读取；有 release 才携带新发布附属数据，无 release 的旧三表快照供 Debug／兼容测试，不启动旧同步。
- `replaceCatalog()` 事务顺序为删旧服务／模型、upsert 服务商、写新模型／服务、删不在新快照的服务商；不对服务商用 REPLACE，防意外级联。正式发布还替换成绩／定义／发布状态，并更新仍可用的收藏身份，任一步失败整体回滚。
- 合法空发布可清目录／成绩，不删除收藏；无 release 的兼容三表替换不碰成绩。Debug 改目录／成绩可能被正式更新覆盖，不加持久覆盖层。
- `hasCatalog()` 判断三表任一记录存在，不以 LLM 筛选结果替代；首次导入还检查发布标记，已提交空包不重复初始化。
- `hasSameContentAs()` 按稳定主键比全部映射内容及发布身份，忽略抓取时间／字典顺序。增删及内容变化均自动提交，相同内容不写库；无待确认候选，后台缓存与列表展示快照的边界见[架构](architecture.md#数据与状态流转)。

| 查询 | 读取边界 |
| --- | --- |
| `observeModelRows()` | 模型左连接服务商，保留无关联模型，不把未匹配服务变成模型；只取规范字段、不含价格 |
| `observeOfferingPriceRows()` | `canonicalModelId` 与 `pricing` 非空的服务，按模型／服务商／服务键稳定返回；不在 SQL 挑价格／档位 |
| `observeModelDetail(id)` | 单事务嵌套 Relation：模型、全部关联服务／服务商、成绩及可选定义；无价不排除，无服务合法，不存在返回 null |
| `observeBenchmarkRankings()` | 单事务批量定义／成绩／当前模型最小身份，避免逐模型读取或不同发布 Flow 拼接 |

列表两条投影由仓库组合后去重、收录、选报价、按发布时间排序；DAO 的名称顺序只是稳定输入，不是最终浏览排序。

## 本地收藏

- `favorites` 无目录外键，不参与远端替换；只存时间、最后可用 `name`／`lab`，不留历史参数／价格／完整详情，不按同名迁移 ID。
- `FavoriteDao.add()` 事务校验当前模型及 LLM 收录，以 IGNORE 插入；重复不改时间，删除幂等，重新收藏记新时间。
- 目录事务只更新仍可用收藏身份、保留收藏时间；下架、空包不删收藏。投影按收藏时间倒序、同时间规范 ID 兜底，模型目录对可用收藏改用目录发布时间顺序。
- `observeFavorites()` 同事务 Relation 读收藏、可选模型及服务，复用收录／报价映射；不存在或不收录只返历史身份，同 ID 恢复后显示当前卡片。
- 这里只保证本地持久化，不承诺卸载重装／跨设备恢复；操作／转场边界见[收藏状态](architecture.md#收藏的数据与状态)。

## 独立评测成绩

- `benchmark_scores` 的 `modelId`／`benchmarkId` 非空，无模型外键，分别建索引；一个模型／评测允许多个配置，ID 按模型路径＋来源数组下标生成，保留原身份。
- 原数值 `score` 为可空 REAL／Double，文本 `scoreText` 原样保存、导入二选一；不提取文本中心值或补零。Debug 至少填写一种，有限数值才合法。
- 可空元数据：`name`、`version`、`variant`、`cost`、`metric`、`unit`、`higherIsBetter`、`subset`、`harness`、`reasoningEffort`、`toolsEnabled`、`sourceUrl`、`sourceType`、`evaluatedAt`。cost 为 REAL／Double、口径与币种未定，不计算或显示；未知布尔／日期保留，不推断可比性。
- 新成绩名称取 name、旧记录回退 benchmarkId；唯一精确匹配才关联定义，否则 `unmapped:` 隔离，不套单位／方向／中文。匹配规则见[接入契约](atlas-data.md#成绩及本地化)。
- 成绩／定义／release 正式同事务替换，详情同事务关联；旧 `models.benchmarks` 已移除，旧版本表仅兼容回归，不复用其递增版本逻辑。

## 列与本地 JSON

值对象位于 `local/model/`，与 DTO／应用模型独立；`CatalogTypeConverters` 使用 Room3 `@ColumnTypeConverter`／`@ColumnTypeConverters`。本地为驼峰 JSON，不直接存远端 DTO。

| Entity | JSON TEXT 字段 |
| --- | --- |
| `ModelEntity` | `inputModalities`、`outputModalities`、`weights`、`links`、`parameters` |
| `ProviderEntity` | `environmentVariables`（名称，不是用户密钥） |
| `ModelOfferingEntity` | `inputModalities`、`outputModalities`、`reasoningOptions`、`interleaved`、`pricing`、`providerOverride`、`experimentalModes` |
| `BenchmarkDefinitionEntity` | `compatibleVersions` |

- null 写 SQL NULL，区分 `[]`、`{}`、全字段缺失的价格对象；未知对象字段可忽略，非法 JSON／已知类型错误／未知密封变体抛错，不伪造成默认值。
- `InterleavedLocal` 用 `{"type":"toggle","enabled":false}` 或 `{"type":"field","field":"reasoning_content"}`，不是远端布尔／对象格式；推理选项保留 null，模式保持字典键，body／headers 仅存储。
- `ModelParametersLocal` 只在规范模型，可空 TEXT；总量／激活量 BigDecimal、B 单位，专用数字序列化器不经过 Double。来源／说明／组件原顺序保留，不扩服务／收藏／列表投影。数值与来源校验见[参数契约](atlas-data.md#模型参数量)。
- `OfferingPricingLocal` 分开保存七类单价、完整 tiers、旧兼容字段及模式价，不落库“选中报价”或自动最低价；计价解释见[价格语义](data-source.md#报价范围与适用条件)。
- JSON 当前不承担复杂 SQL 筛选／排序；将来明确需要再评估列化，不假定设备具备 SQLite JSON 扩展。

## Debug 数据库管理

- 仅 Debug 开放原五表逐条 CRUD，定义／release 不开放；顶层统计只算三目录表，管理概览另算五表及库／WAL／SHM 近似大小，不含 DataStore 或 Room 内部表。
- 按主键稳定分页、每页 50，主键／名称搜索，成绩按主键／benchmark ID；不轮询、不联网、不任意 SQL／批量清空。新增填写主键，已有主键不可改；显式 SQL NULL、空字符串／集合、可空布尔 NULL／0／1，整数校验 64 位。
- `DebugDatabaseStore` 用同一 Room 连接、固定表／列白名单、参数化语句和事务，实际 Schema／主键不符禁操作。写删比较原始整行避免覆盖并发修改，服务商级联数量删除前显示、事务复核；所有删除确认，收藏不可撤销。
- 仅写改变列，未改 JSON 不重写／丢未知字段；损坏 JSON 可查看修复，输入按本地类型校验，参数按相同数值／来源规则，不接受远端蛇形结构替代。
- 行编辑不走收藏业务校验／身份同步，可构造不可用收藏；删模型不删服务／收藏，删服务商级联服务。刷新仍同步可用身份，可能覆盖调试改动，不暂停自动检查。
- `providerOverride`／`experimentalModes` 默认隐藏可主动展开，不入诊断复制／日志；草稿只在 ViewModel 内存，配置变化可保留、进程死不恢复，不写 SavedState／库／剪贴板。
- 提交后 `onCatalogMutatedExternally()` 仅重读本地并发布列表展示快照，不二次联网、不恢复持续目录观察。测试入口集中于[分级测试](testing.md)，实际执行／遗留问题只在 [TODO](../TODO.md#待验收与发布)与历史记录维护。
