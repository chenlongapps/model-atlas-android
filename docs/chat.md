# AI 文本聊天

## 当前范围与入口

设置 →「AI 助手」打开独立子页，不增加底栏标签。主 Pager 保留组合但不放置，不暴露交互／读屏；退出恢复设置及目录阅读状态。聊天使用全局 Material 3 主题，纯文本、多行输入、流式回复、复制、主动停止和显式重试；没有 Markdown 渲染依赖，也不连接本地目录、评测、实时价格或工具。

进入后点右上角「会话配置」，填写：

- **API Base URL**：完整 API 根路径，例如 `https://api.example.com/v1/` 或 `https://gateway.example.com/openai/v1`；不自动补 `/v1`，不要填写 `chat/completions`。
- **API Key**：服务商提供的 Bearer 密钥。
- **Model ID**：该服务实际接受的模型 ID，不自动使用 ModelAtlas 目录规范 ID。

应用配置后输入消息并发送。未配置／非法配置不请求，不提供伪造回复。配置校验复用 `LlmConfig` 和客户端共用的 URL 安全规则；应用配置本身不探测网络。更改任意配置会清空原对话，避免跨服务意外发送历史。重试会替换本轮部分结果、重新发起可能计费的请求，只有末轮失败／停止可重试。

## 状态与调用生命周期

- `ui/chat/ChatRoute.kt` 获取 Activity 宿主的 Hilt `ChatViewModel`，生命周期感知收集；`ChatScreen` 仅接收状态和回调，可独立预览／测试。
- `ChatViewModel` 直接协调单会话，无仅透传的 `ChatRepository`。`StateFlow<ChatUiState>` 管理输入、配置草稿、轮次列表和防重入；稳定 `ChatTurn` 各自暴露回复 `StateFlow`，增量仅更新当前条目，不逐 token 复制历史列表或重组整个页面。
- 只有发送／显式重试创建 `viewModelScope` Job 并收集一次 `LlmClient.stream()`；重组、界面状态恢复、前后台重新收集不启动新请求。`content` 按增量拼接；空增量不伪造文本，`finishReason`／usage 不提前结束等待，冷流正常结束才标记 `COMPLETE`，真实客户端仍验证 `[DONE]`。
- 回复状态分为等待、生成、完成、失败、停止。异常不记录／展示任意 Throwable 内容，只映射安全错误类型；保留部分文本。取消继续抛出 `CancellationException`，底层取消／关闭由既有客户端负责。停止与离页均取消 Job（也覆盖尚未调度的 Job）；配置重建不取消，ViewModel 销毁结构化取消。不提供后台服务或自动恢复请求。
- 后续请求仅携带此前 `COMPLETE` 轮次的 user／assistant 成对有序上下文，再附当前 user；失败／停止轮的两条消息都排除。重试复用原轮次 user，只重置 assistant，不重复添加用户消息、不自动续写或付费重放。不自动裁剪完整上下文，超出服务限制由明确错误反馈处理。
- 正向 `LazyColumn` 保留旧条目及同一条长回复中部的文字锚点。跟随只在末尾／主动发送时启用；用户向上滚动停用，回到末尾恢复。流式跟随与视口变化处理独立于历史列表，最多每帧更新一次。输入区限制最大高度、内部滚动，系统栏／刘海／IME 只消费一次；系统返回优先收起键盘，顶部返回直接退出。

## 内存与敏感信息

配置、配置草稿、输入和消息仅存在于 ViewModel 内存，Activity 配置重建保留，离页重入复用；进程结束丢失。没有 DataStore、Room、SavedStateHandle、Saver、BuildConfig、资源或 `local.properties` 运行时凭据读取。可保存的仅为子页开关和 UI 阅读／跟随位置，不保存密钥、消息或配置。

配置和草稿的 `toString()` 脱敏；密钥输入掩码、密码键盘、关闭纠错，配置弹窗启用 secure flag。复制消息是用户显式操作，剪贴板标记敏感；不提供复制密钥操作。不记录凭据、提示词、响应或原始错误，不引入日志拦截器。开发机 `local.properties` 仍仅允许显式 JVM 线上测试读取，见 [LLM API](llm-api.md)。

## 验证与后续

`ChatViewModelTest` 使用 Fake `LlmClient`，不访问真实付费服务；`ChatScreenTest` 覆盖流式／停止／失败／配置、状态恢复及生命周期重新收集、复制、长回复阅读锚点、窄屏大字号 RTL／横屏和真实 IME 返回；`ChatNavigationTest` 覆盖子页隐藏、返回及来源状态恢复。实际执行结果记入[开发历史](archive/development-history.md)，未验收项记入 [TODO](../TODO.md)。客户端 SSE／socket 取消与资源关闭仍由 `OpenAiCompatibleClientTest` 回归。

尚未实现凭据／历史持久化、多会话管理、复杂 Markdown、附件／语音、Function Calling、Agent、MCP、RAG 或本地目录注入；真实 TalkBack、跨厂商动态色、系统边缘手势及真实服务聊天需要另行验收。
