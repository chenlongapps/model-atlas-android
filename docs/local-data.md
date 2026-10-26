# 本地目录数据

## 当前实现与边界

已定义并接通三张 Room3 目录实体、独立收藏表、本地值对象和 JSON 列转换器；`CatalogDatabase`、`CatalogDao` 与 `RoomCatalogStore` 构成目录存储，支持本地优先读取、后台检查与主动更新。收藏由 `FavoriteDao` 与 `RoomFavoriteRepository` 独立管理，不进入远端快照。

- `CatalogMapper` 实现 DTO → Entity，保留整个目录与完整复杂字段；数据库名为 `catalog.db`，由 Hilt 配置 `AndroidSQLiteDriver`。
- Repository 并行请求两个端点，均成功后形成完整快照；后台检查发现规范模型 ID 增删时暂存完整快照，待用户确认；ID 集合不变、仅内容变化时静默以全量快照事务更新三表，不显示更新提醒。主动下拉刷新直接同步完整变化。规范模型与服务商左连接 Flow 驱动真实 LLM 列表。
- `hasCatalog()` 用三表存在性查询判断本地是否有记录，不用筛选后的 LLM 数量代替；`readCatalog()` 在单个事务中读取三表完整 Entity，供目录比较，不改变 Schema v1。`CatalogSnapshot.hasSameModelIdsAs()` 仅比较规范模型 `id` 集合，决定是否需要确认增删，不比较内容或服务商／模型服务。`hasSameContentAs()` 仍按主键比较全部内容、忽略 `fetchedAtEpochMillis` 与字典顺序，其他已映射字段与缺失值语义均保留，供静默同步和主动刷新判断是否写库。待应用快照仅在仓库内存暂存，不增加持久化表或迁移；相同内容不重复写库。
- 收藏、筛选状态、最低价等业务数据不混入目录实体；当前不建设多数据源框架。
- 报价投影 `observeOfferingPriceRows()` 只取显式关联到规范模型且 `pricing` 非空的服务，按 `(canonicalModelId, providerId, id)` 排序；规范模型元数据仍由 `observeModelRows()` 单独提供，两条 Flow 在仓库层合并，互不覆盖。
- 详情使用 `observeModelDetail(id)`：按稳定 ID 查询规范模型，通过 Room3 `@Relation(parentColumns / entityColumns)` 读取所有显式关联的服务和服务商，整个嵌套读取在 `@Transaction` 内完成，避免目录替换时混入不同快照。无报价服务不排除，无关联模型返回空服务列表，不存在模型返回 null；只新增查询与投影，不修改 Schema v1。
- 测试目录中的 `CatalogSchemaTestDatabase` 仅用于 Room KSP 编译校验，不创建运行时数据库。
- Debug 顶层调试页通过既有 `CatalogStore.readCatalog()` 在 IO dispatcher 读取一致的完整快照，仅保留三张表的数量；切入且分页停稳、手动重读及刷新结束后读取，离屏预组合不读库，不做 LLM 筛选或持续观察。手动刷新仍走正式仓库的原子提交，不新增调试写库、Schema 或 DAO 接口。页面明确标注统计口径、读取失败与旧值。

## 三张表的职责

下表仅列出远端目录；用户收藏是第四张独立表，见下文。

源码路径相对于 [`data/local/`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/)。

| 实体 | 表名 | 主键 | 内容 |
| --- | --- | --- | --- |
| [`ModelEntity`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/entity/ModelEntity.kt) | `models` | `id` | 规范模型的元数据、能力、token 限制、权重、链接和评测，不包含服务商报价 |
| [`ProviderEntity`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/entity/ProviderEntity.kt) | `providers` | `id` | API 服务商名称、地址、文档、SDK 包与环境变量名称，不嵌套模型服务列表 |
| [`ModelOfferingEntity`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/entity/ModelOfferingEntity.kt) | `model_offerings` | `(providerId, id)` | 某服务商的模型服务，独立保存能力、token 限制、生命周期、报价、推理选项和模式 |

模型与服务商不是同一种实体；当前远程 DTO 没有独立的开发商字段，不根据 API 服务商或 ID 前缀认定开发商。列表 LAB 仅按界面约定展示稳定 ID 的前缀标签，不改变存储或关联规则。

## 标识、关联与来源

以下约定由 `CatalogMapper` 显式实现；DTO 本身仍只描述远程结构，不定义本地主键。

- `ModelEntity.id` 使用 `models.json` 的顶层字典键。
- `ProviderEntity.id` 使用 `api.json` 的顶层字典键。
- `ModelOfferingEntity.id` 使用该服务商 `models` 字典的键；它只在该服务商内唯一，因此与 `providerId` 构成复合主键，不使用自增或字符串拼接 ID。
- 三种记录均以 `remoteId` 独立保留 DTO 条目内的 `id`，不把字典键与条目 ID 当成必然相同的字段。发现不一致时不能通过名称、前缀或模糊匹配修正关联。
- `providerId` 是指向 `providers.id` 的外键，删除服务商时级联删除其服务。复合主键以 `providerId` 开头，不再建立重复的单列索引。
- `canonicalModelId` 只保留上游显式的 `canonical_model_id`，可空并建立普通索引，**不设数据库外键**。两个端点不同步、规范模型未收录或记录暂缺时，仍可保存服务及其原始关联。后续查询采用可选关联，不丢弃未匹配的服务。
- `source` 当前默认为 `models.dev`；数据库只面向当前单一目录来源，不因存在该字段就假定支持多个来源共用 ID。
- `fetchedAtEpochMillis` 是调用方必须显式传入的本地获取时间（Unix 毫秒）；同一响应中的记录应使用一致的获取时间，不在实体构造时读取系统时钟。
- `sourceLastUpdated` 保留模型或服务的上游 `last_updated` 字符串，不代表本地刷新时间或价格生效时间。日期和知识截止时间不强制转换成完整日期。

服务自身的字段使用其 DTO 的信息，不用规范模型覆盖缺失的服务能力或限制。`replaceCatalog()` 在同一事务中删除旧服务与模型、upsert 服务商、插入新模型与服务，再删除本次快照不存在的服务商。不使用服务商 `REPLACE`，避免意外级联；任一步失败时三表一起回滚。有效空快照会清空目录。

`observeModelRows()` 通过 `canonicalModelId` 左连接，按规范模型名称与 ID、服务商名称与 ID 排序并去重；没有关联的模型保留空服务商，未匹配的服务仍保存在表中但不生成伪规范模型。投影包含规范模型的发布时间、上下文／输出 token 限制、输入／输出模态、开放权重与许可，不取服务覆盖值。`observeOfferingPriceRows()` 是另一条投影，只返回 `canonicalModelId`、服务商 ID 与名称、服务内键和 `pricing`，不重复规范模型字段，也不在 SQL 内选择档位或报价。列表映射层按规范模型 ID 去重、执行 LLM 收录规则并按报价投影分组挑选展示报价，不再将服务商与描述传递给列表界面；最终列表顺序由映射层按发布时间倒序决定（最新在前，缺失或空白发布时间排在最后，相同时间按名称和稳定 ID 稳定排序），DAO 的名称排序仅作为去重前的稳定输入；来源规则见[数据源说明](data-source.md)。

## 本地收藏

- `favorites` 以规范模型 ID（`modelId`）为主键，保存 `favoritedAtEpochMillis`、最后可用的 `name` 和 `lab`。不保存历史参数、报价、完整详情或服务商选择，不按同名迁移 ID。
- 不设指向 `models` 的外键：目录全量替换、下架或空快照都不删除收藏。目录比较、`hasCatalog()` 和调试统计仍只计算三张目录表。
- `FavoriteDao.add()` 在事务内读取并校验当前模型及 LLM 收录规则，再以 IGNORE 插入；重复添加不改变时间。删除幂等；重新收藏使用新的时间。列表按收藏时间倒序，同时间按规范 ID 稳定排序。
- `CatalogDao.replaceCatalog()` 在原有事务内，仅为本次仍可用的已收藏模型更新名称与 LAB；不改变收藏时间，不可用时保留最后身份，失败时与目录一起回滚。
- `observeFavorites()` 通过嵌套 Relation 在单个事务内读取收藏、可选规范模型及关联服务／服务商；仓库复用 LLM 收录和默认报价映射，避免独立 Flow 在刷新时短暂拼接出失效模型或旧价格。不存在或不再收录时只返回收藏身份，同 ID 恢复后重新显示当前卡片。
- 项目尚未发布，按本次约定继续使用 Schema **v1**，直接更新导出的 `1.json`，不提供旧开发 Schema 的迁移，也不启用破坏性重建兜底。已有旧 Schema 的开发安装需要自行清除数据或重装；正式发布后再从此基线维护升级迁移。本地持久化不承诺卸载重装或跨设备恢复。
- `FavoriteDatabaseTest` 覆盖排序／重复／重新收藏、最后可用身份、下架与重新出现、零价与报价消失、失败回滚、刷新并发、不可用收藏文件重开。ViewModel 与界面另覆盖本地重试、保存失败不返回、防重复提交及来源返回。

## 列与本地 JSON

名称、描述、系列、类型、日期、能力、token 限制以及服务的状态均为普通列；能力使用 `Boolean?`，token 数使用 `Long?`。类型、状态和模态保持开放字符串，不使用封闭枚举。

以下字段通过 [`CatalogTypeConverters`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/converter/CatalogTypeConverters.kt) 存为 JSON TEXT：

| 实体 | JSON 字段 |
| --- | --- |
| `ModelEntity` | `inputModalities`、`outputModalities`、`weights`、`links`、`benchmarks` |
| `ProviderEntity` | `environmentVariables`（仅变量名称，不是用户密钥） |
| `ModelOfferingEntity` | `inputModalities`、`outputModalities`、`reasoningOptions`、`interleaved`、`pricing`、`providerOverride`、`experimentalModes` |

- 值对象位于 [`local/model/`](../app/src/main/java/io/github/chenlongapps/modelatlas/data/local/model/)，不引用远程 DTO；本地 JSON 使用自己的驼峰字段名，不能直接把远程 JSON 写入列中。
- Room3 使用 `@ColumnTypeConverter` 和 `@ColumnTypeConverters`，转换器已注册到各实体。
- `null` 写为 SQL NULL，与空列表 `[]`、空字典 `{}`、存在但所有价格缺失的报价对象区分。
- 未知对象字段允许忽略；非法 JSON、已知字段的错误类型和未知的本地密封变体抛出序列化异常，不静默变成空列表、零价或不支持。
- `InterleavedLocal` 使用稳定的本地变体标记：`{"type":"toggle","enabled":false}` 或 `{"type":"field","field":"reasoning_content"}`；不复用远程的布尔值 / 对象格式。
- `reasoningOptions.values` 保留列表中的 `null`；模式名称保持字典键；覆盖参数的 `body` 保留任意 JSON 对象，headers 保留字符串字典，仅存储、不执行。

JSON 字段目前不承担复杂 SQL 筛选或排序。未来明确需要按单价、模态等执行数据库查询时，再评估列化或拆表，不依赖设备一定具备特定 SQLite JSON 扩展。

## 报价与缺失值

`OfferingPricingLocal` 保存输入、输出、缓存读写、音频输入输出、推理单价，以及完整的 `tiers` 条件和旧版 `contextOver200k`。模式报价与基础报价分别保存，不计算统一最低价，不自动合并或选择档位。

- 目录报价使用 `Double?`，不是财务结算类型。
- 缺失价格与明确的 `0`、缺失能力与明确的 `false`、缺失集合与空集合均分别保存。
- LLM 报价按 USD / 百万 tokens 理解；不能据此为非 LLM 推导可比较的 token 价格。
- 精确门槛、旧字段兼容、缓存条件、推理计费与展示约束以[数据源说明](data-source.md)为准，本地存储不增加计价解释。
- 展示报价由映射层按规范模型 ID 分组后选取，不在数据库或 DAO 内决定展示哪一家服务商，也不落库“选中报价”之类的派生状态。

## 已有验证与后续测试

- `CatalogTypeConvertersTest` 覆盖全部转换器的空值和往返转换、空集合 / 对象、零价、精确档位与旧字段、可空推理选项、交错推理变体、动态模式、自由 JSON、未知字段和非法数据。
- `CatalogEntitiesTest` 覆盖可选字段默认未知、独立标识、显式获取时间、服务自身的能力 / 限制，以及超出 Int 的 token 数和不完整日期。
- `CatalogSchemaTestDatabase` 通过 `app/build.gradle.kts` 中的 `kspTest` 显式启用 Room 处理器，让测试编译阶段校验四实体的字段类型、复合主键、外键、索引和转换器声明；这不等同于数据库读写或级联删除测试。

`androidTest` 的 `CatalogDatabaseTest` 验证实际 SQLite 读写、跨服务商重复服务 ID、显式与缺失关联、快照替换、外键失败时整笔回滚和文件数据库重开，并验证完整快照读取、目录存在性查询、报价投影只取显式关联且有报价的服务、未匹配报价行不会变成模型、无报价时不生成报价行；详情查询另覆盖所有显式关联服务（含无报价）、服务商嵌套读取、无关联模型和模型移除。生产 Schema v1 导出到 `app/schemas/`，本次无需升级。目录比较、模型增删待确认且不写库、内容静默同步、应用快照、离线展示和刷新失败保留数据由仓库／ViewModel 单元测试覆盖。详情字段精简不删除底层记录级时间、完整报价或其他元数据；参数量无可靠来源，不新增列。目录页统一更新时间仍未实现。任务进度只在 [TODO.md](../TODO.md) 维护。
