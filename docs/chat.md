# AI 文本聊天

## 当前范围与入口

底部圆形 `AI` 按钮打开独立全屏路由，不属于主 Pager。支持纯文本、多行输入、流式回复、复制、停止和显式重试；不连接本地目录、评测、实时价格或工具。

导航边界见[架构](architecture.md#顶层分页与子页面)，共享底栏变形、页面布局与 IME 避让见[设计规范](Design.md#ai-助手)，请求协议见 [LLM API](llm-api.md)。

进入后点右上角「会话配置」，填写：

- **API Base URL**：完整 API 根路径，例如 `https://api.example.com/v1/` 或 `https://gateway.example.com/openai/v1`；不自动补 `/v1`，不要填写 `chat/completions`。
- **API Key**：服务商提供的 Bearer 密钥。
- **Model ID**：该服务实际接受的模型 ID，不自动使用 ModelAtlas 目录规范 ID。

应用配置并保存成功后才可发送；未配置／非法配置不请求、不伪造回复。表单复用 `LlmConfig` 的 URL 安全校验，应用配置不探测网络。配置变化清空对话，避免跨服务发送历史；保存、恢复、失败与确认清除见[AI 配置持久化](ai-configuration.md)。

## 状态与调用生命周期

### 状态归属

- `ModelAtlasApp` 在 NavDisplay 外获取 Activity 级 Hilt `ChatViewModel`；同一 `uiState` 驱动共享输入栏与 `ui/chat/ChatRoute.kt`，pop 不销毁内存对话。
- 外壳传 `State<ChatUiState>` 和输入／发送／停止回调给底栏，输入变化只重组底栏；向页面传预留高度、路由可见性和返回回调。`ChatScreen` 仅含标题、消息、配置和返回处理，无编辑器，可独立预览／测试。
- ViewModel 直接协调单会话，不加透传 Repository。`StateFlow<ChatUiState>` 管输入、配置草稿、轮次与防重入；稳定 `ChatTurn` 各有回复 Flow，增量不复制历史列表或重组整页。

### 请求与上下文

- 仅发送／显式重试创建 `viewModelScope` Job 并收集一次 `LlmClient.stream()`；重组、恢复和生命周期重新收集不发请求。
- 回复为等待／生成／完成／失败／停止。按增量拼接，空增量不造文本；`finishReason`／usage 不提前结束，冷流正常结束才标记 `COMPLETE`，客户端负责验证 `[DONE]`。
- 后续请求仅带此前 `COMPLETE` 轮次的 user／assistant 有序对，再附当前 user；失败／停止轮两条均排除。不自动裁剪上下文，超限明确报错。
- 仅末轮失败／停止可重试：保留原 user，重置 assistant、替换部分结果，可能再次计费；不重复用户消息、不自动续写或重放。
- 错误只映射安全类型、保留部分文本，不记录／展示任意 Throwable。`CancellationException` 继续传播，底层取消和关闭交客户端负责。

### 离页与返回

| 事件 | 行为 |
| --- | --- |
| 停止／确认退出 | 同步取消 Job，含尚未调度的 Job；不依赖退出动画或 DisposableEffect |
| 预测性返回取消 | 保留路由、阅读、对话与进行中请求 |
| Activity 配置重建 | 保留 ViewModel，不取消、不重复请求 |
| ViewModel 销毁 | 结构化取消；无后台服务或自动恢复 |
| 退出过渡 | 立即停用输入、配置弹窗、跟随及 IME 返回；配置草稿留内存，重入才恢复弹窗 |

未进入助手时不组合聊天页。系统／按钮返回先收 IME，再 pop；弹窗优先。退出清焦点、收键盘但不清草稿，重入不自动唤键盘；不接入模型／榜单共享容器。

### 阅读与跟随

正向 `LazyColumn` 保留旧条目及长回复中部文字锚点。末尾或主动发送时跟随；上滚停用、回到末尾恢复，新轮次恢复跟随并滚到底。流式跟随与视口变化独立于历史列表，最多每帧更新一次。

## 配置与敏感信息

已提交配置通过独立 `LlmConfigRepository` 持久化；配置草稿、输入和消息仅存在于 ViewModel 内存，Activity 配置重建保留，离页重入复用，进程结束丢失。聊天不使用 Room、SavedStateHandle、Saver、BuildConfig、资源或 `local.properties` 保存／读取运行时凭据。UI 可保存的仅为路由标识、主分页选择和阅读／跟随位置，不包含凭据、消息或配置草稿。

配置和草稿的 `toString()` 脱敏；密钥输入掩码、密码键盘、关闭纠错，配置弹窗启用 secure flag。复制消息是用户显式操作，剪贴板标记敏感；不提供复制密钥操作。不记录凭据、提示词、响应或原始错误，不引入日志拦截器。开发机 `local.properties` 仍仅允许显式 JVM 线上测试读取，见 [LLM API](llm-api.md)。

## 验证与后续

选测入口见[分级测试](testing.md)，请求层回归仍用 `OpenAiCompatibleClientTest`，聊天测试使用 Fake 客户端、不默认调用付费服务。实际结果记[历史](archive/development-history.md)，未验收项记 [TODO](../TODO.md)。

尚未实现历史持久化、多会话管理、复杂 Markdown、附件／语音、Function Calling、Agent、MCP、RAG 或本地目录注入；真实 TalkBack、跨厂商动态色、系统边缘手势及真实服务聊天需要另行验收。
